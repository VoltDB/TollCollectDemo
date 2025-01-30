package com.voltdb.tollcollect.client;

import org.voltdb.VoltTable;
import org.voltdb.client.Client2;
import org.voltdb.client.Client2Config;
import org.voltdb.client.ClientFactory;
import org.voltdb.client.ClientResponse;
import org.voltdb.client.ProcCallException;

import java.io.Console;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.NoSuchElementException;


public class TollCollector implements AutoCloseable {

    private static final String DEFAULT_VOLT_HOST = "localhost";
    private static final String DEFAULT_VOLT_PORT = "21212";

    private final String servers;
    private final Client2 client;

    public TollCollector(String servers) {
        this.servers = servers;

        // Create a client instance with default configuration values
        Client2Config config = new Client2Config();
        client = ClientFactory.createClient(config);
    }

    public static void main(String[] args) {
        Console console = System.console();
        String server = getServerFromUser(console);

        // Instantiate object to handle Volt connection
        try (TollCollector tc = new TollCollector(server)) {
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
        if (connectionInput != null) {
            servers = connectionInput;
        }
        return servers;
    }

    // Method to establish connection with Volt using Client2
    void connectToVolt() throws IOException {
        // Connect to Volt using a string for connection details and synchronous completion
        client.connectSync(servers);
        System.out.println("Connected to Volt.\n");
    }

    @Override
    public void close() {
        client.close();
    }

    // Method used to process plate
    void processScanRow(long scanTimestamp, String location, String lane, String plateNum, String vehicleClass)
            throws IOException, ProcCallException {
        System.out.println("Looking up plate... Calculating toll... Writing to scan log... ");
        // Call ProcessPlate procedure with synchronous completion and return response into ClientResponse object
        callProcedureSynchronously("ProcessPlate", scanTimestamp, location, lane, plateNum, vehicleClass);
    }

    // Method used to call AdHoc SQL and return query result as VoltTable
    VoltTable callAdHocSQL(Object... arguments) throws IOException, ProcCallException {
        // Call AdHoc procedure by specifying "@AdHoc" as first parameter, followed by string containing SQL code and parameters
        return client.callProcedureSync("@AdHoc", arguments).getResults()[0];
    }

    // Method used to charge account
    void chargeRow(long scanId, long scanTimestamp, String location, String lane, String plateNum, int accountId, BigDecimal tollAmount, String tollReason)
            throws IOException, ProcCallException {
        System.out.println("Checking balance... Charging account... Writing to account log... ");
        // Call ChargeAccount procedure with synchronous completion and return response into ClientResponse object
        callProcedureSynchronously("ChargeAccount", scanId, scanTimestamp, location, lane, plateNum, accountId, tollAmount, tollReason);
    }

    private void callProcedureSynchronously(String procedure, Object... arguments) throws IOException, ProcCallException {
        ClientResponse chargeAccountResponse = client.callProcedureSync(procedure, arguments);
        // Report transaction performance metrics available in ClientResponse object
        System.out.printf("Estimated Database Transaction Time: %.3fs | End-to-end Transaction Time: %.3fs%n",
                (chargeAccountResponse.getClusterRoundtrip() / 1000.0),
                (chargeAccountResponse.getClientRoundtrip() / 1000.0));
    }
}
