/*
 * Copyright (C) 2025 Volt Active Data Inc.
 *
 * Use of this source code is governed by an MIT
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */
package com.voltdb.tollcollect.client;

import org.voltdb.VoltTable;
import org.voltdb.client.Client2;
import org.voltdb.client.Client2Config;
import org.voltdb.client.ClientFactory;
import org.voltdb.client.ClientResponse;
import org.voltdb.client.ProcCallException;

import java.io.IOException;
import java.math.BigDecimal;


public class TollCollectorDbClient implements AutoCloseable {

    private final String servers;
    private final Client2 client;

    public TollCollectorDbClient(String servers) {
        this.servers = servers;

        // Create a client instance with default configuration values
        Client2Config config = new Client2Config();
        client = ClientFactory.createClient(config);
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
        return callProcedureSynchronously("@AdHoc", arguments)[0];
    }

    // Method used to charge account
    void chargeRow(long scanId, long scanTimestamp, String location, String lane, String plateNum, int accountId, BigDecimal tollAmount, String tollReason)
            throws IOException, ProcCallException {
        System.out.println("Checking balance... Charging account... Writing to account log... ");
        // Call ChargeAccount procedure with synchronous completion and return response into ClientResponse object
        callProcedureSynchronously("ChargeAccount", scanId, scanTimestamp, location, lane, plateNum, accountId, tollAmount, tollReason);
    }

    private VoltTable[] callProcedureSynchronously(String procedure, Object... arguments) throws IOException, ProcCallException {
        ClientResponse response = client.callProcedureSync(procedure, arguments);
        if (response.getStatus() != ClientResponse.SUCCESS) {
            System.out.printf("Error calling procedure %s: %s%n", procedure, response.getStatusString());
        }

        // Report transaction performance metrics available in ClientResponse object
        System.out.printf("Estimated Database Transaction Time: %.3fs | End-to-end Transaction Time: %.3fs%n",
                (response.getClusterRoundtrip() / 1000.0),
                (response.getClientRoundtrip() / 1000.0));

        return response.getResults();
    }
}
