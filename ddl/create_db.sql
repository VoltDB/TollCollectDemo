--
-- Copyright (C) 2025 Volt Active Data Inc.
--
-- Use of this source code is governed by an MIT
-- license that can be found in the LICENSE file or at
-- https://opensource.org/licenses/MIT.
--


-- All DDL statements until END_OF_BATCH will succeed or fail together...
--
file -inlinebatch END_OF_BATCH


-------------- REPLICATED TABLES ------------------------------------------------
-- Define tables that will hold static or slow moving reference data and do not
-- need to be partitioned

CREATE TABLE VEHICLE_TYPES (
  vehicle_type      SMALLINT           NOT NULL, --Vehicle type ID
  vehicle_class     VARCHAR(20)        NOT NULL, --Text description of vehicle type
  toll_multip       DECIMAL            NOT NULL, --multiplier adjustment to base toll
  PRIMARY KEY (vehicle_type)
);

CREATE UNIQUE INDEX vt_uk_1 ON VEHICLE_TYPES (vehicle_class);

CREATE TABLE TOLL_LOCATIONS (
  toll_loc_id       SMALLINT           NOT NULL, --Unique ID for entry
  toll_loc          VARCHAR(64)        NOT NULL, --Location of toll
  toll_loc_status   TINYINT            NOT NULL, --0 = inactive, 1 = active
  base_fare         DECIMAL            NOT NULL, --Base fare charge at toll location in USD
  latitude          DECIMAL            NOT NULL,
  longitude         DECIMAL            NOT NULL,
  PRIMARY KEY (toll_loc_id)
);

CREATE UNIQUE INDEX tl_uk_1 ON TOLL_LOCATIONS (toll_loc);

CREATE TABLE APPLICATION_PARAMETERS
(parameter_name varchar(30) not null primary key
,parameter_value varchar(40) not null);

-------------- PARTITIONED TABLES -----------------------------------------------
-- Define tables that should be spread across database partitions for parallel processing
-- Partition by column

CREATE TABLE KNOWN_VEHICLES (
  plate_num         VARCHAR(20)       NOT NULL, --String containing vehicle registration plate
  account_id        INTEGER           NOT NULL, --Account owner of the vehicle
  vehicle_type      SMALLINT          NOT NULL, --Type of vehicle. See toll table.
  active            TINYINT           NOT NULL, --0 = inactive, 1 = active
  exempt_status     TINYINT           NOT NULL, --0 = not exempt, 1 = exempt
  PRIMARY KEY (plate_num)
);
PARTITION TABLE KNOWN_VEHICLES ON COLUMN plate_num;

CREATE TABLE SCAN_HISTORY (
scan_id           BIGINT            NOT NULL,
scan_timestamp    TIMESTAMP         NOT NULL,
plate_num         VARCHAR(20)       NOT NULL, --String containing vehicle registration plate
account_id        INTEGER,
toll_loc          VARCHAR(64)       NOT NULL,
toll_lane_num     VARCHAR(2)        NOT NULL,
toll_amount       DECIMAL           NOT NULL,
toll_reason       VARCHAR(200),
scan_fee_amount   DECIMAL,
total_amount      DECIMAL           NOT NULL,
PRIMARY KEY (plate_num, scan_id))
USING TTL 3600 SECONDS ON COLUMN scan_timestamp BATCH_SIZE 200 MAX_FREQUENCY 1;

PARTITION TABLE SCAN_HISTORY ON COLUMN plate_num;

CREATE TABLE ACCOUNTS (
                          account_id        INTEGER           NOT NULL, --Unique ID assigned on account creation
                          account_status    TINYINT           NOT NULL, --0 = inactive, 1 = active
                          auto_topup        TINYINT           NOT NULL, --0 = not enrolled, 1 = enrolled
                          balance           DECIMAL           NOT NULL, --Prepaid balance in account in USD
                          PRIMARY KEY (account_id)
);
PARTITION TABLE ACCOUNTS ON COLUMN account_id;

CREATE TABLE ACCOUNT_HISTORY (
   acct_tx_id        INTEGER           NOT NULL,
   acct_tx_timestamp TIMESTAMP         NOT NULL,
   account_id        INTEGER           NOT NULL,
   plate_num         VARCHAR(20),
   scan_id           INTEGER,
   scan_timestamp    TIMESTAMP,
   toll_loc          VARCHAR(64),
   toll_lane_num     VARCHAR(2),
   toll_amount       DECIMAL,
   toll_reason       VARCHAR(200),
   tx_fee_amount     DECIMAL,
   total_amount      DECIMAL           NOT NULL,
   tx_type           VARCHAR(6)        NOT NULL, --DEBIT for tolls, CREDIT for top ups.
--  PRIMARY KEY (acct_tx_id)
);
PARTITION TABLE ACCOUNT_HISTORY ON COLUMN account_id;

-------------- STREAMS ----------------------------------------------------------
-- Define output stream tables for ephemeral processing
-- Potential uses include generating materialized views, exporting to external
-- systems, and/or writing to Volt topics for external consumers
-- Data will not persist after processing

CREATE STREAM bill_by_mail_export PARTITION ON COLUMN plate_num EXPORT TO TARGET bill_by_mail_service (
  scan_id           INTEGER           NOT NULL,
  scan_timestamp    TIMESTAMP         NOT NULL,
  plate_num         VARCHAR(20)       NOT NULL, --String containing vehicle registration plate
  toll_loc          VARCHAR(20)       NOT NULL,
  toll_lane_num     VARCHAR(2)        NOT NULL,
  toll_amount       DECIMAL           NOT NULL,
  toll_reason       VARCHAR(20),
  scan_fee_amount   DECIMAL,
  tx_fee_amount     DECIMAL,
  total_amount      DECIMAL           NOT NULL,
);


CREATE STREAM top_up_export PARTITION ON COLUMN account_id EXPORT TO TARGET top_up_service (
  acct_tx_id        INTEGER           NOT NULL,
  acct_tx_timestamp TIMESTAMP         NOT NULL,
  account_id        INTEGER           NOT NULL,
  topup_amount      DECIMAL           NOT NULL,
);

-------------- VIEWS ----------------------------------------------------------

CREATE VIEW highest_grossing_locations
            (toll_loc, total_toll_amount)
AS SELECT toll_loc, SUM(toll_amount) AS total_toll_amount from SCAN_HISTORY GROUP BY toll_loc;

CREATE VIEW location_scans
            (toll_loc, total_count)
AS SELECT toll_loc, count(*) AS total_count from SCAN_HISTORY GROUP BY toll_loc;

CREATE VIEW invalid_scans_locations
            (toll_loc, invalid_count)
AS SELECT toll_loc, COUNT(*) AS invalid_count from SCAN_HISTORY WHERE toll_reason = 'UNKNOWN_VEHICLE' GROUP BY toll_loc;

-------------- SQL STORED PROCEDURES ---------------------------------------------
CREATE PROCEDURE AddToBalance PARTITION ON TABLE ACCOUNTS COLUMN account_id PARAMETER 1 AS
UPDATE ACCOUNTS SET balance = balance + ?
WHERE account_id = ?;

------------- SQL STORED PROCEDURES USED BY THE DASHBOARD ------------------------

CREATE PROCEDURE dashboard_gross AS
select * from highest_grossing_locations order by TOTAL_TOLL_AMOUNT desc;

CREATE PROCEDURE dashboard_fares AS
select * from TOLL_LOCATIONS order by base_fare, toll_loc_id desc;

CREATE PROCEDURE dashboard_location_scans AS
SELECT * FROM location_scans ORDER BY toll_loc;

CREATE PROCEDURE dashboard_top_10_accounts AS
SELECT * FROM accounts ORDER BY balance desc limit 10;

CREATE PROCEDURE dashboard_vehicle_classes AS
SELECT vehicle_class, toll_multip FROM vehicle_types;

CREATE PROCEDURE dashboard_invalid_scans AS
SELECT     l.toll_loc
     ,     l.latitude
     ,     l.longitude
     ,     hgl.invalid_count AS invalid_count
FROM     TOLL_LOCATIONS l
LEFT JOIN     invalid_scans_locations hgl     ON         l.toll_loc = hgl.toll_loc ORDER BY     l.toll_loc;

-------------- JAVA STORED PROCEDURES --------------------------------------------

CREATE PROCEDURE FROM CLASS com.voltdb.tollcollect.procedures.ChargeAccount;
CREATE PROCEDURE PARTITION ON TABLE scan_history COLUMN plate_num PARAMETER 3
FROM CLASS com.voltdb.tollcollect.procedures.ProcessPlate;

-------------- INDEXES -----------------------------------------------------------
-- Define any indexes for TABLES or VIEWS on columns that are not a PRIMARY KEY.

CREATE INDEX sh_del_idx ON scan_history(plate_num, scan_timestamp, scan_id) ;

CREATE INDEX sh_ttl_idx ON scan_history(scan_timestamp) ;

-------------- SCHEDULED TASKS --------------------------------------------------
-- Define tasks to execute stored procedures on a schedule

-- All statements from the start to here suceed or fail as a unit..
--

END_OF_BATCH

