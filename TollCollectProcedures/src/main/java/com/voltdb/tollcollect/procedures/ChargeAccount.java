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

import java.util.Date;
import java.math.BigDecimal;

public class ChargeAccount extends VoltProcedure {

    public final SQLStmt getAccountInfo = new SQLStmt(
            "SELECT balance, auto_topup, account_status FROM ACCOUNTS " +
            "WHERE account_id = ? AND account_status = 1;"
    );

    public final SQLStmt addTopUpAmount = new SQLStmt(
            "UPDATE ACCOUNTS SET balance = balance + 30.00 " +
            "WHERE account_id = ?;"
    );

    public final SQLStmt exportTopUp = new SQLStmt(
            "INSERT INTO top_up_export VALUES (?, ?, ?, ?);"
    );

    public final SQLStmt updateBalance = new SQLStmt(
            "UPDATE ACCOUNTS SET balance = balance - ? " +
            "WHERE account_id = ?;"
    );

    public final SQLStmt insertAccountHistory = new SQLStmt(
            "INSERT INTO ACCOUNT_HISTORY (acct_tx_id, acct_tx_timestamp, account_id, " +
            "plate_num, scan_id, scan_timestamp, toll_loc, toll_lane_num, " +
            "toll_amount, toll_reason, tx_fee_amount, " +
            "total_amount, tx_type) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);"
    );


    public VoltTable[] run(long scanTimestamp,
                           String location,
                           String lane,
                           String plateNum,
                           int accountId,
                           double tollAmount,
                           String tollReason,
                           double totalAmount) throws VoltAbortException {
        //Initialize transaction variables
        BigDecimal txFeeAmount = new BigDecimal(0);
        final BigDecimal topup_amount = new BigDecimal(30);
        BigDecimal finalTotalAmount = new BigDecimal(0);

        // Use Volt to generate safe uniqueId and timestamp for this transaction.
        int acct_tx_id = (int) this.getUniqueId();
        Date acct_tx_timestamp = this.getTransactionTime();

        // Get account information
        voltQueueSQL(getAccountInfo, accountId);
        VoltTable[] accountResults = voltExecuteSQL();

        if (accountResults[0].getRowCount() == 0) {
            throw new VoltAbortException("Invalid account");
        }

        VoltTableRow accountRow = accountResults[0].fetchRow(0);
        BigDecimal currentBalance = accountRow.getDecimalAsBigDecimal("balance");
        byte autoTopup = (byte) accountRow.get("auto_topup", VoltType.TINYINT);


        // Check if balance after deduction would be below threshold
        BigDecimal projectedBalance = currentBalance.subtract(new BigDecimal(tollAmount));

        if (projectedBalance.compareTo(new BigDecimal(10)) == -1 && projectedBalance.compareTo(new BigDecimal(0)) == 1 && autoTopup == 1) {
            // Process auto top-up
            voltQueueSQL(addTopUpAmount, accountId, topup_amount);

            // Send to top-up export stream to charge payment method. Assume successful.
            voltQueueSQL(exportTopUp,
                    acct_tx_id,
                    acct_tx_timestamp,
                    accountId,
                    topup_amount);


            // Record top-up in account history
            voltQueueSQL(insertAccountHistory,
                    acct_tx_id,
                    acct_tx_timestamp,
                    accountId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "AUTO_TOPUP",
                    null,
                    topup_amount,
                    "CREDIT");
        } else if (projectedBalance.compareTo(new BigDecimal(0)) == -1 && autoTopup == 0) {
            // Add administrative fee
            txFeeAmount = new BigDecimal(25);
            finalTotalAmount = txFeeAmount.add(new BigDecimal(tollAmount));

            // Send to bill-by-mail export stream. Separate export stream recommended to avoid MP transaction.
//            voltQueueSQL(exportBillByMail,
//                    scanID,
//                    scanTimestamp,
//                    plateNum,
//                    location,
//                    lane,
//                    tollAmount,
//                    "INSUFFICIENT_BALANCE",
//                    null,
//                    txFeeAmount,
//                    finalTotalAmount);

        }

        // Update account balance
        voltQueueSQL(updateBalance, finalTotalAmount, accountId);

        // Record toll transaction in account history
        voltQueueSQL(insertAccountHistory,
                acct_tx_id,
                acct_tx_timestamp,
                accountId,
                plateNum,
                getUniqueId(),
                scanTimestamp,
                location,
                lane,
                tollAmount,
                tollReason,
                txFeeAmount,
                finalTotalAmount,
                "DEBIT");

        return voltExecuteSQL();
    }
}
