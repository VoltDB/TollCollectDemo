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
import java.math.BigDecimal;

public class ProcessPlate extends VoltProcedure {

    // This section defines SQL statements to be executed as part of this transaction
    // This two statements will look up base toll for a toll location and vehicle multiplier
    public final SQLStmt getTollInfo = new SQLStmt(
            "SELECT base_fare FROM TOLL_LOCATIONS WHERE toll_loc = ? AND toll_loc_status = 1;"
    );

    public final SQLStmt getAppParam = new SQLStmt(
            "SELECT parameter_value FROM application_parameters WHERE parameter_name = ?;"
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

   // Delete up to 2 old scan history records
   public static final SQLStmt removeOldestTransaction = new SQLStmt("DELETE "
              + "FROM scan_history "
              + "WHERE plate_num = ? "
              + "AND scan_timestamp < DATEADD(MINUTE, (-1 * ?),NOW) "
              + "ORDER BY scan_timestamp, scan_id, plate_num LIMIT 2;");

    public long run(
            long scanTimestamp, 
            String location,
            String lane,
            String plateNum,
            String vehicleClass) throws VoltAbortException {

        final long scanId = getUniqueId();

        // Set default values for parameters
	long keepMinutes = 10;

        //Initialize toll calculation variables
        BigDecimal baseToll;
        BigDecimal vehicleMultiplier;
        BigDecimal scanFeeAmount = new BigDecimal(0);
        BigDecimal tollAmount;
        BigDecimal totalAmount;
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

        // Get how long to keep records for
        voltQueueSQL(getAppParam, "KEEP_MINUTES");
        VoltTable[] paramResults = voltExecuteSQL();
        if (paramResults[0].advanceRow()) {
            try {
                keepMinutes = Long.parseLong(paramResults[0].getString("PARAMETER_VALUE"));
                }
            catch (NumberFormatException e) {
                throw new VoltAbortException("Invalid keep minutes of " + paramResults[0].getString("PARAMETER_VALUE"));
                }
        }

        baseToll = tollResults[0]
                .fetchRow(0)
                .getDecimalAsBigDecimal("base_fare");

        vehicleMultiplier = vehicleResults[0]
                .fetchRow(0)
                .getDecimalAsBigDecimal("toll_multip");

        tollAmount = baseToll.multiply(vehicleMultiplier);

        // Check if vehicle is known
        voltQueueSQL(checkVehicle, plateNum);
        VoltTable[] vehicleCheckResults = voltExecuteSQL();
        if (vehicleCheckResults[0].getRowCount() > 0) {
            VoltTableRow vehicleRow = vehicleCheckResults[0].fetchRow(0);
            accountId = (int) vehicleRow.getLong("account_id");
            exemptStatus = (byte) vehicleRow.get("exempt_status", VoltType.TINYINT);

            if (exemptStatus == 1) {
                tollAmount = new BigDecimal(0);
                totalAmount = new BigDecimal(0);
                tollReason = "EXEMPT";
            } else {
                totalAmount = tollAmount;
                tollReason = "STANDARD TOLL ( " + vehicleClass + ")";
            }
        } else {
            // Unknown vehicle or mismatched type
            scanFeeAmount = new BigDecimal(2);
            totalAmount = tollAmount.add(scanFeeAmount);
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
                scanId, new java.util.Date(scanTimestamp), plateNum, accountId,
                location, lane, tollAmount, tollReason, scanFeeAmount, totalAmount);

        // Delete stale scan history records
        voltQueueSQL(removeOldestTransaction, plateNum, keepMinutes);

        voltExecuteSQL(true);

        return 0;
    }
}
