/*
 * Copyright (C) 2025 Volt Active Data Inc.
 *
 * Use of this source code is governed by an MIT
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */
package com.voltdb.tollcollect.client;

import org.voltdb.VoltTable;
import org.voltdb.client.ProcCallException;

import java.io.Console;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.NoSuchElementException;


public class TollCollector {

    private static final String DEFAULT_VOLT_HOST = "localhost";
    private static final int DEFAULT_VOLT_PORT = 21212;

    public static void main(String[] args) {
        Console console = System.console();
        String server = getServerFromUser(console);

        // Instantiate object to handle Volt connection
        try (TollCollectorDbClient tc = new TollCollectorDbClient(server)) {
            // Connect to Volt
            try {
                tc.connectToVolt();
            } catch (IOException ex) {
                System.out.println("Unable to connect.");
                System.exit(-1);
            }

            System.out.println("Enter plate scan info below (Ctrl-d to stop)");

            while (true) {
                long scanTimestamp = System.currentTimeMillis();
                String location = console.readLine("Enter toll location [Infinity Bridge|Echo Lane Station]: ");
                String lane = console.readLine("Enter toll lane [01-05]: ");
                String plateNum = console.readLine("Enter plate number [X000-X999]: ");
                String vehicleClass = console.readLine("Enter vehicle type [Car|Motorcycle|Small Truck|Large Truck|Bus]: ");

                try {
                    // Perform Volt procedure call to process plate
                    tc.processScanRow(scanTimestamp, location, lane, plateNum, vehicleClass);
                } catch (IOException | ProcCallException e) {
                    System.err.println("Error processing scan: " + e.getMessage());
                    continue;
                }

                System.out.println("Checking Entry...");

                VoltTable scanResult;
                // Perform Volt AdHoc SQL query, collect results, and print results with a well formatted string.
                try {
                    scanResult = tc.callAdHocSQL("SELECT * FROM SCAN_HISTORY WHERE plate_num = ? " +
                                                 "AND toll_loc = ? AND toll_lane_num = ? " +
                                                 "ORDER BY scan_timestamp DESC LIMIT 1", plateNum, location, lane);
                    System.out.println(scanResult.toFormattedString());
                } catch (IOException | ProcCallException e) {
                    System.err.println("Error retrieving data: " + e.getMessage());
                    continue;
                }

                // Read through the query result table and assign variables for further work
                if (scanResult.advanceRow() && scanResult.getLong("ACCOUNT_ID") > 0) {
                    long scanId = scanResult.getLong("SCAN_ID");
                    int accountId = (int) scanResult.getLong("ACCOUNT_ID");
                    BigDecimal tollAmount = scanResult.getDecimalAsBigDecimal("TOLL_AMOUNT");
                    String tollReason = scanResult.getString("TOLL_REASON");

                    try {
                        // Perform Volt procedure call to charge account
                        tc.chargeRow(scanId, scanTimestamp, location, lane, plateNum, accountId, tollAmount, tollReason);
                    } catch (IOException | ProcCallException e) {
                        System.err.println("Error charging account: " + e.getMessage());
                        continue;
                    }

                    System.out.println("Checking Entry...");
                    // Perform Volt AdHoc SQL query, collect results, and print results with a well formatted string.
                    try {
                        VoltTable chargeResult = tc.callAdHocSQL("SELECT * FROM ACCOUNT_HISTORY WHERE plate_num = ? " +
                                                                 "AND toll_loc = ? AND toll_lane_num = ? " +
                                                                 "ORDER BY acct_tx_timestamp DESC LIMIT 1", plateNum, location, lane);
                        System.out.println(chargeResult.toFormattedString());
                    } catch (IOException | ProcCallException e) {
                        System.err.println("Error retrieving data: " + e.getMessage());
                    }
                } else {
                    System.out.println("No account transaction needed");
                }
            }
        } catch (NoSuchElementException | IllegalStateException ex) {
            // NoSuchElementException to catch Ctrl-d (EOF)
            // IllegalStateException to catch Scanner errors
            System.out.println("\nInput complete. Goodbye!");
        }
    }

    private static String getServerFromUser(Console console) {
        String connectionInput = console.readLine(
                "Enter VoltDB connection details (host:port) or press ENTER key for default [%s:%d]:",
                DEFAULT_VOLT_HOST,
                DEFAULT_VOLT_PORT
        );

        // If provided, user input will replace default connection details for Volt.
        String servers = DEFAULT_VOLT_HOST + ":" + DEFAULT_VOLT_PORT;
        if (connectionInput != null && !connectionInput.isEmpty()) {
            servers = connectionInput;
        }
        return servers;
    }
}
