package com.voltdb.tollcollect.client;

import org.voltdb.VoltTable;
import org.voltdb.client.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Scanner;


public class TollCollector {
    static final String DEFAULT_VOLT_HOST = "localhost";
    static final String DEFAULT_VOLT_PORT = "21212";
    private String servers = "";

    // Create a class variable for the Volt Client
    Client2 client;

    public static void main(String[] args) {
        // Instantiate object to handle Volt connection
        TollCollector tc = new TollCollector();
        // Instantiate object to handle user input
        Scanner sc = new Scanner(System.in);

        // Declare VoltTable objects to hold results of database queries
        VoltTable scanResult;
        VoltTable chargeResult;


        System.out.print("Enter VoltDB connection details " +
                "[" + DEFAULT_VOLT_HOST + ":" + DEFAULT_VOLT_PORT + "] or press ENTER key for default: ");
        String connectionInput = sc.nextLine();
        // If provided, user input will replace default connection details for Volt.
        // Supports a list of servers, each with host and optional port.
        tc.servers = connectionInput.isEmpty() ? DEFAULT_VOLT_HOST + ":" + DEFAULT_VOLT_PORT : connectionInput;


        // Connect to Volt
        try {
            tc.connectToVolt();
        } catch (IOException ex) {
            System.out.println("Unable to connect.");
            sc.close();
            System.exit(-1);
        }

        System.out.println("Enter plate scan info below (Ctrl-d to stop)");

        try {
            while (true) {
                long scanTimestamp = System.currentTimeMillis();
                System.out.print("Enter toll location [Infinity Bridge|Echo Lane Station]: ");
                String location = sc.nextLine();
                System.out.print("Enter toll lane [01-05]: ");
                String lane = sc.nextLine();
                System.out.print("Enter plate number [X000-X999]: ");
                String plateNum = sc.nextLine();
                System.out.print("Enter vehicle type [Car|Motorcycle|Small Truck|Large Truck|Bus]: ");
                String vehicleClass = sc.nextLine();
                try {
                    // Perform Volt procedure call to process plate
                    tc.processScanRow(scanTimestamp, location, lane, plateNum, vehicleClass);
                } catch (IOException | ProcCallException e) {
                    System.err.println("Error processing scan: " + e.getMessage());
                    continue;
                }
                System.out.println("Checking Entry...");

                scanResult = null;
                // Perform Volt AdHoc SQL query, collect results, and print results with a well formatted string.
                try {
                    scanResult = tc.callAdHocSQL("SELECT * FROM SCAN_HISTORY WHERE plate_num = ? " +
                            "AND toll_loc = ? AND toll_lane_num = ? " +
                            "ORDER BY scan_timestamp DESC LIMIT 1", plateNum, location, lane);
                    System.out.println(scanResult.toFormattedString());
                } catch (IOException | ProcCallException e) {
                    System.err.println("Error retrieving data: " + e.getMessage());
                }
                // Read through the query result table and assign variables for further work
                if (scanResult != null
                        && scanResult.getRowCount() > 0
                        && (int) scanResult.fetchRow(0).getLong("ACCOUNT_ID") > 0) {
                    long scanId = scanResult.fetchRow(0).getLong("SCAN_ID");
                    int accountId = (int) scanResult.fetchRow(0).getLong("ACCOUNT_ID");
                    BigDecimal tollAmount = scanResult.fetchRow(0).getDecimalAsBigDecimal("TOLL_AMOUNT");
                    String tollReason = scanResult.fetchRow(0).getString("TOLL_REASON");

                    try {
                        // Perform Volt procedure call to charge account
                        tc.chargeRow(scanId, scanTimestamp, location, lane, plateNum, accountId, tollAmount, tollReason);
                    } catch (IOException | ProcCallException e) {
                        System.err.println("Error charging account: " + e.getMessage());
                        continue;
                    }
                    System.out.println("Checking Entry...");

                    chargeResult = null;
                    // Perform Volt AdHoc SQL query, collect results, and print results with a well formatted string.
                    try {
                        chargeResult = tc.callAdHocSQL("SELECT * FROM ACCOUNT_HISTORY WHERE plate_num = ? " +
                                "AND toll_loc = ? AND toll_lane_num = ? " +
                                "ORDER BY acct_tx_timestamp DESC LIMIT 1", plateNum, location, lane);
                        System.out.println(chargeResult.toFormattedString());
                    } catch (IOException | ProcCallException e) {
                        System.err.println("Error retrieving data: " + e.getMessage());
                    }
                }
                else {
                    System.out.println("No account transaction needed");
                }


            }
        } catch (NoSuchElementException | IllegalStateException ex) {
            // NoSuchElementException to catch Ctrl-d (EOF)
            // IllegalStateException to catch Scanner errors
            System.out.println("\nInput complete. Goodbye!");
        } finally {
            // Close scanner
            sc.close();
            try {
                // Close Volt Connection
                tc.disconnectFromVolt();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }


    // Method to establish connection with Volt using Client2
    void connectToVolt() throws IOException {
        Client2Config config = new Client2Config();
        // Create a client instance with default configuration values
        client = ClientFactory.createClient(config);
        // Connect to Volt using a string for connection details and synchronous completion
        client.connectSync(servers);
        System.out.println("Connected to Volt \n");
    }

    // Method to close connection with Volt
    void disconnectFromVolt() throws InterruptedException {
        client.drain();
        client.close();
        client = null;
    }

    // Method used to process plate
    void processScanRow(long scanTimestamp, String location, String lane, String plateNum, String vehicleClass)
            throws IOException, ProcCallException {
        System.out.println("Looking up plate... Calculating toll... Writing to scan log... ");
        // Call ProcessPlate procedure with synchronous completion and return response into ClientResponse object
        ClientResponse processPlateResponse = client.callProcedureSync("ProcessPlate", scanTimestamp,location,lane,plateNum,vehicleClass);
        // Report transaction performance metrics available in ClientResponse object
        System.out.printf("Estimated Database Transaction Time: %.3fs | End-to-end Transaction Time: %.3fs%n",
                (processPlateResponse.getClusterRoundtrip() / 1000.0),
                (processPlateResponse.getClientRoundtrip() / 1000.0));
    }

    // Method used to call AdHoc SQL and return query result as VoltTable
    VoltTable callAdHocSQL(String sqlCode, String plateNum, String location, String lane) throws IOException, ProcCallException {
        // Call AdHoc procedure by specifying "@AdHoc" as first parameter, followed by string containing SQL code and parameters
        return client.callProcedureSync("@AdHoc", sqlCode, plateNum, location, lane).getResults()[0];
    }

    // Method used to charge account
    void chargeRow(long scanId, long scanTimestamp, String location, String lane, String plateNum, int accountId, BigDecimal tollAmount, String tollReason)
            throws IOException, ProcCallException {
        System.out.println("Checking balance... Charging account... Writing to account log... ");
        // Call ChargeAccount procedure with synchronous completion and return response into ClientResponse object
        ClientResponse chargeAccountResponse = client.callProcedureSync("ChargeAccount", scanId, scanTimestamp, location, lane, plateNum, accountId, tollAmount,tollReason);
        // Report transaction performance metrics available in ClientResponse object
        System.out.printf("Estimated Database Transaction Time: %.3fs | End-to-end Transaction Time: %.3fs%n",
                (chargeAccountResponse.getClusterRoundtrip() / 1000.0),
                (chargeAccountResponse.getClientRoundtrip() / 1000.0));
    }
}



