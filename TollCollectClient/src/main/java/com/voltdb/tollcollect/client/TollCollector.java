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
        // Object to handle Volt connection
        TollCollector tc = new TollCollector();
//         Object to handle user input
        Scanner sc = new Scanner(System.in);

        VoltTable scanResult = null;
        VoltTable chargeResult = null;


        System.out.print("Enter VoltDB connection details " +
                "[" + DEFAULT_VOLT_HOST + ":" + DEFAULT_VOLT_PORT + "] or press ENTER key for default: ");
        String connectionInput = sc.nextLine();
        tc.servers = connectionInput.isEmpty() ? DEFAULT_VOLT_HOST + ":" + DEFAULT_VOLT_PORT : connectionInput;


        //connect to Volt
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
                    tc.processScanRow(scanTimestamp, location, lane, plateNum, vehicleClass);
                } catch (IOException | ProcCallException e) {
                    System.err.println("Error processing scan: " + e.getMessage());
                }
                System.out.println("Checking Entry...");
                try {
                    scanResult = tc.callAdHocSQL("SELECT * FROM SCAN_HISTORY WHERE plate_num = ? " +
                            "AND toll_loc = ? AND toll_lane_num = ? " +
                            "ORDER BY scan_timestamp DESC LIMIT 1", plateNum, location, lane);
                    System.out.println(scanResult.toFormattedString());
                } catch (IOException | ProcCallException e) {
                    System.err.println("Error retrieving data: " + e.getMessage());
                }
                if (scanResult != null
                        && scanResult.getRowCount() > 0
                        && (int) scanResult.fetchRow(0).getLong("ACCOUNT_ID") > 0) {
                    long scanId = scanResult.fetchRow(0).getLong("SCAN_ID");
                    int accountId = (int) scanResult.fetchRow(0).getLong("ACCOUNT_ID");
                    BigDecimal tollAmount = scanResult.fetchRow(0).getDecimalAsBigDecimal("TOLL_AMOUNT");
                    String tollReason = scanResult.fetchRow(0).getString("TOLL_REASON");

                    try {
                        tc.chargeRow(scanId, scanTimestamp, location, lane, plateNum, accountId, tollAmount, tollReason);
                    } catch (IOException | ProcCallException e) {
                        System.err.println("Error charging account: " + e.getMessage());
                    }
                    System.out.println("Checking Entry...");
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



    void connectToVolt() throws IOException {
        Client2Config config = new Client2Config();
        client = ClientFactory.createClient(config);
        client.connectSync(servers);
        System.out.println("Connected to Volt \n");
    }

    void disconnectFromVolt() throws InterruptedException {
        client.drain();
        client.close();
    }


    void processScanRow(long scanTimestamp, String location, String lane, String plateNum, String vehicleClass)
            throws IOException, ProcCallException {
        System.out.println("Looking up plate... Calculating toll... Writing to scan log... ");
        ClientResponse processPlateResponse = client.callProcedureSync("ProcessPlate", scanTimestamp,location,lane,plateNum,vehicleClass);
        System.out.printf("Estimated Database Transaction Time: %.3fs | End-to-end Transaction Time: %.3fs%n",
                (processPlateResponse.getClusterRoundtrip() / 1000.0),
                (processPlateResponse.getClientRoundtrip() / 1000.0));
    }

    VoltTable callAdHocSQL(String sqlCode, String plateNum, String location, String lane) throws IOException, ProcCallException {
        return client.callProcedureSync("@AdHoc", sqlCode, plateNum, location, lane).getResults()[0];
    }

    void chargeRow(long scanId, long scanTimestamp, String location, String lane, String plateNum, int accountId, BigDecimal tollAmount, String tollReason)
            throws IOException, ProcCallException {
        System.out.println("Checking balance... Charging account... Writing to account log... ");
        ClientResponse chargeAccountResponse = client.callProcedureSync("ChargeAccount", scanId, scanTimestamp, location, lane, plateNum, accountId, tollAmount,tollReason);
        System.out.printf("Estimated Database Transaction Time: %.3fs | End-to-end Transaction Time: %.3fs%n",
                (chargeAccountResponse.getClusterRoundtrip() / 1000.0),
                (chargeAccountResponse.getClientRoundtrip() / 1000.0));
    }
}



