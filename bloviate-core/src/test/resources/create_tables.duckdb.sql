--
-- Copyright 2020 Tim Veil
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

-- Comprehensive type coverage for DuckDB (issue #451). DuckDB is embedded, so this runs with no
-- Docker; see DuckDBFillerTest.

CREATE TYPE mood AS ENUM ('ok', 'sad', 'happy');

-- the types that map through the cross-database defaults
CREATE TABLE standard_table (
    c_boolean   BOOLEAN       NOT NULL,
    c_tinyint   TINYINT       NOT NULL,
    c_smallint  SMALLINT      NOT NULL,
    c_integer   INTEGER       NOT NULL,
    c_bigint    BIGINT        NOT NULL,
    c_float     FLOAT         NOT NULL,
    c_double    DOUBLE        NOT NULL,
    c_decimal   DECIMAL(12,3) NOT NULL,
    c_varchar   VARCHAR       NOT NULL,
    c_varchar_n VARCHAR(40)   NOT NULL,
    c_blob      BLOB          NOT NULL,
    c_date      DATE          NOT NULL,
    c_time      TIME          NOT NULL,
    c_timestamp TIMESTAMP     NOT NULL,
    c_timestamptz TIMESTAMPTZ NOT NULL
);

-- the types DuckDB reports in a way the defaults get wrong, and that DuckDBSupport handles
CREATE TABLE special_types (
    c_utinyint     UTINYINT     NOT NULL,
    c_usmallint    USMALLINT    NOT NULL,
    c_uinteger     UINTEGER     NOT NULL,
    c_ubigint      UBIGINT      NOT NULL,
    c_hugeint      HUGEINT      NOT NULL,
    c_uhugeint     UHUGEINT     NOT NULL,
    c_uuid         UUID         NOT NULL,
    c_json         JSON         NOT NULL,
    c_bit          BIT          NOT NULL,
    c_enum         mood         NOT NULL,
    c_timestamp_s  TIMESTAMP_S  NOT NULL,
    c_timestamp_ms TIMESTAMP_MS NOT NULL,
    c_timestamp_ns TIMESTAMP_NS NOT NULL
);

-- DuckDB enforces primary and foreign keys, so the dependency-ordered fill applies as it does on
-- an OLTP store; this pins that a child's key lands on a parent row
CREATE TABLE parents (
    id   INTEGER     NOT NULL PRIMARY KEY,
    name VARCHAR(40) NOT NULL
);

CREATE TABLE children (
    id        INTEGER NOT NULL PRIMARY KEY,
    parent_id INTEGER NOT NULL REFERENCES parents (id)
);
