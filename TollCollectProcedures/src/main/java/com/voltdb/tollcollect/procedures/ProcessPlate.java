/*
 * Copyright (C) 2025 Volt Active Data Inc.
 *
 * Use of this source code is governed by an MIT
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */
package com.voltdb.tollcollect.procedures;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltTable;
import org.voltdb.VoltTableRow;
import org.voltdb.VoltType;

public class ProcessPlate extends VoltProcedure {

    // This section defines SQL statements to be executed as part of this transaction
    // This two statements will look up base toll for a toll location and vehicle multiplier
    public final SQLStmt getTollInfo = new SQLStmt(
            "SELECT base_fare FROM TOLL_LOCATIONS WHERE toll_loc = ? AND toll_loc_status = 1;"
    );

    public final SQLStmt getVehicleMultiplier = new SQLStmt(
            "SELECT toll_multip FROM VEHICLE_TYPES WHERE vehicle_class = ?;"
    );

    // SQL statement to check known vehicles
    public final SQLStmt checkVehicle = new SQLStmt(
            "SELECT account_id, exempt_status, vehicle_type FROM KNOWN_VEHICLES " +
            "WHERE plate_num = ? AND active = 1;"
    );

    // Insert into bill_by_mail_export stream
    public final SQLStmt exportBillByMail = new SQLStmt(
            "INSERT INTO bill_by_mail_export VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);"
    );

    // SQL statement to insert scan history
    public final SQLStmt insertScanHistory = new SQLStmt(
            "INSERT INTO SCAN_HISTORY VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);"
    );

    public long run(
            long scanTimestamp,
            String location,
            String lane,
            String plateNum,
            String vehicleClass) throws VoltAbortException {
        long scanId = getUniqueId();

        //Initialize toll calculation variables
        double baseToll;
        double vehicleMultiplier;
        double scanFeeAmount = 0.00;
        double tollAmount;
        double totalAmount;
        String tollReason;

        //Initialize lookup values
        int accountId = 0;
        byte exemptStatus;

        // Get base toll for location
        voltQueueSQL(getTollInfo, location);
        VoltTable[] tollResults = voltExecuteSQL();
        if (tollResults[0].getRowCount() == 0) {
            throw new VoltAbortException("Invalid toll location");
        }

        // Get vehicle type multiplier
        voltQueueSQL(getVehicleMultiplier, vehicleClass);
        VoltTable[] vehicleResults = voltExecuteSQL();
        if (vehicleResults[0].getRowCount() == 0) {
            throw new VoltAbortException("Invalid vehicle class");
        }

        baseToll = tollResults[0]
                .fetchRow(0)
                .getDecimalAsBigDecimal("base_fare")
                .doubleValue();

        vehicleMultiplier = vehicleResults[0]
                .fetchRow(0)
                .getDecimalAsBigDecimal("toll_multip")
                .doubleValue();

        tollAmount = baseToll * vehicleMultiplier;

        // Check if vehicle is known
        voltQueueSQL(checkVehicle, plateNum);
        VoltTable[] vehicleCheckResults = voltExecuteSQL();
        if (vehicleCheckResults[0].getRowCount() > 0) {
            VoltTableRow vehicleRow = vehicleCheckResults[0].fetchRow(0);
            accountId = (int) vehicleRow.getLong("account_id");
            exemptStatus = (byte) vehicleRow.get("exempt_status", VoltType.TINYINT);

            if (exemptStatus == 1) {
                tollAmount = 0.00;
                totalAmount = 0.00;
                tollReason = "EXEMPT";
            } else {
                totalAmount = tollAmount;
                tollReason = "STANDARD TOLL ( " + vehicleClass + ")";
            }
        } else {
            // Unknown vehicle or mismatched type
            scanFeeAmount = 2.00;
            totalAmount = tollAmount + scanFeeAmount;
            tollReason = "UNKNOWN_VEHICLE";

//            // Insert into bill_by_mail_export stream
//            voltQueueSQL(exportBillByMail,
//                    scanId, scanTimestamp, plateNum, location, lane,
//                    tollAmount, tollReason, scanFeeAmount, null, tollAmount
//            );
//
//            voltExecuteSQL();
        }

        // Insert into scan history
        voltQueueSQL(insertScanHistory,
                scanId, scanTimestamp, plateNum, accountId,
                location, lane, tollAmount, tollReason, scanFeeAmount, totalAmount);

        voltExecuteSQL(true);

        return 0;
    }
}
