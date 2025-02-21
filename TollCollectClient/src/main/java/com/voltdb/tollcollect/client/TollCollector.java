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

public class TollCollector {

    private static final String DEFAULT_VOLT_HOST = "localhost";
    private static final int DEFAULT_VOLT_PORT = 21212;

    private final Console console;

    TollCollector(Console console) {
        this.console = console;
    }

    public static void main(String[] args) {
        new TollCollector(System.console()).run();
    }

    void run() {
        String server = getServerFromUser(console);

        // Instantiate object to handle Volt connection
        try (TollCollectorDbClient tc = new TollCollectorDbClient(server)) {
            // Connect to Volt
            try {
                System.out.println("Connecting to Volt at " + server);
                tc.connectToVolt();
            } catch (IOException ex) {
                System.out.println("Unable to connect.");
                System.exit(-1);
            }

            System.out.println("Enter plate scan info below (Ctrl-c to stop)");

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
                // Perform Volt query, collect results, and print results with a well formatted string.
                try {
                    scanResult = tc.getPlateHistory(plateNum, location, lane,1);
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
                    // Perform Volt query, collect results, and print results with a well formatted string.
                    try {
                        VoltTable chargeResult = tc.getAccountHistory(accountId, 10);
                        System.out.println(chargeResult.toFormattedString());
                    } catch (IOException | ProcCallException e) {
                        System.err.println("Error retrieving data: " + e.getMessage());
                    }
                } else {
                    System.out.println("No account found");
                }
            }
        } catch (RuntimeException e) {
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
