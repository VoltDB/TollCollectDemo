--
-- Copyright (C) 2025 Volt Active Data Inc.
--
-- Use of this source code is governed by an MIT
-- license that can be found in the LICENSE file or at
-- https://opensource.org/licenses/MIT.
--

DROP PROCEDURE ChargeAccount IF EXISTS;
DROP PROCEDURE ProcessPlate IF EXISTS;
DROP PROCEDURE AddToBalance IF EXISTS;

DROP PROCEDURE dashboard_gross IF EXISTS;
DROP PROCEDURE dashboard_fares IF EXISTS;
DROP PROCEDURE dashboard_location_scans IF EXISTS;
DROP PROCEDURE dashboard_top_10_accounts IF EXISTS;
DROP PROCEDURE dashboard_vehicle_classes IF EXISTS;
DROP PROCEDURE dashboard_invalid_scans IF EXISTS; 

DROP VIEW highest_grossing_locations IF EXISTS;
DROP VIEW location_scans IF EXISTS;
DROP VIEW invalid_scans_locations IF EXISTS;

DROP TABLE VEHICLE_TYPES IF EXISTS;
DROP TABLE TOLL_LOCATIONS IF EXISTS;
DROP TABLE KNOWN_VEHICLES IF EXISTS;
DROP TABLE SCAN_HISTORY IF EXISTS;
DROP TABLE ACCOUNTS IF EXISTS;
DROP TABLE ACCOUNT_HISTORY IF EXISTS;
DROP TABLE APPLICATION_PARAMETERS IF EXISTS;

DROP STREAM bill_by_mail_export IF EXISTS;
DROP STREAM top_up_export IF EXISTS;


