-- BigQuery test schema for BigQueryFillerTest.
--
-- Substituted tokens: ${dataset} (the target dataset) and ${suffix} (a per-run id, so repeated or
-- concurrent runs never collide). Every table carries a 2-hour expiration so a failed run cannot
-- leave billable tables behind.
--
-- Only natively bindable types appear here. DatabaseFiller fills every table in the dataset, so a
-- single DATETIME/JSON/GEOGRAPHY/ARRAY/STRUCT column would fail the whole fill; rejection of those
-- is covered by BigQuerySupportTest instead.
--
-- Keys are declared NOT ENFORCED, which is the only form BigQuery accepts. They are never enforced
-- at write time, but the driver surfaces them through getPrimaryKeys/getImportedKeys, which is what
-- drives Bloviate's dependency graph and foreign-key value alignment.

CREATE TABLE `${dataset}.region_${suffix}` (
    r_regionkey INT64 NOT NULL,
    r_name      STRING(25),
    r_comment   STRING(152),
    PRIMARY KEY (r_regionkey) NOT ENFORCED
) OPTIONS(expiration_timestamp = TIMESTAMP_ADD(CURRENT_TIMESTAMP(), INTERVAL 2 HOUR));

CREATE TABLE `${dataset}.nation_${suffix}` (
    n_nationkey INT64 NOT NULL,
    n_regionkey INT64 NOT NULL,
    n_name      STRING(25),
    n_comment   STRING(152),
    PRIMARY KEY (n_nationkey) NOT ENFORCED,
    FOREIGN KEY (n_regionkey) REFERENCES `${dataset}.region_${suffix}`(r_regionkey) NOT ENFORCED
) OPTIONS(expiration_timestamp = TIMESTAMP_ADD(CURRENT_TIMESTAMP(), INTERVAL 2 HOUR));

-- Every natively bindable type, in both its bare and parameterized form where one exists, so the
-- size and precision clamps are exercised against the real service.
CREATE TABLE `${dataset}.standard_types_${suffix}` (
    c_int64          INT64,
    c_float64        FLOAT64,
    c_numeric        NUMERIC,
    c_numeric_scaled NUMERIC(10, 2),
    c_bignumeric     BIGNUMERIC,
    c_bool           BOOL,
    c_string         STRING,
    c_string_sized   STRING(20),
    c_bytes          BYTES,
    c_bytes_sized    BYTES(16),
    c_date           DATE,
    c_time           TIME,
    c_timestamp      TIMESTAMP
) OPTIONS(expiration_timestamp = TIMESTAMP_ADD(CURRENT_TIMESTAMP(), INTERVAL 2 HOUR));

-- Any real BigQuery table is partitioned and clustered; this proves the batch/DML path works
-- against such a destination rather than only against a plain heap table.
CREATE TABLE `${dataset}.events_${suffix}` (
    e_id     INT64 NOT NULL,
    e_name   STRING(50),
    e_amount NUMERIC(12, 2),
    e_ts     TIMESTAMP NOT NULL,
    PRIMARY KEY (e_id) NOT ENFORCED
)
PARTITION BY DATE(e_ts)
CLUSTER BY e_id
OPTIONS(expiration_timestamp = TIMESTAMP_ADD(CURRENT_TIMESTAMP(), INTERVAL 2 HOUR));
