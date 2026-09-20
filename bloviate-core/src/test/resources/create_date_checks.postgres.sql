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

-- Issue #619: first-of-period CHECKs in both spellings on DATE, TIMESTAMP and TIMESTAMPTZ columns,
-- next to the enum / IN / BETWEEN checks the parser already honoured. One table per group so the
-- explicit-generator test can override the date columns without touching the rest.

CREATE TYPE ticket_state AS ENUM ('OPEN', 'HELD', 'CLOSED');

CREATE TABLE month_dates (
    id            integer NOT NULL,
    d_trunc       date                     NOT NULL CHECK (date_trunc('month', d_trunc) = d_trunc),
    d_extract     date                     NOT NULL CHECK (EXTRACT(day FROM d_extract) = 1),
    ts_trunc      timestamp                NOT NULL CHECK (date_trunc('month', ts_trunc) = ts_trunc),
    ts_extract    timestamp                NOT NULL CHECK (EXTRACT(day FROM ts_extract) = 1),
    tstz_trunc    timestamp with time zone NOT NULL CHECK (date_trunc('month', tstz_trunc) = tstz_trunc),
    tstz_extract  timestamp with time zone NOT NULL CHECK (EXTRACT(day FROM tstz_extract) = 1),
    d_quarter     date                     NOT NULL CHECK (date_trunc('quarter', d_quarter) = d_quarter),
    tstz_year     timestamp with time zone NOT NULL CHECK (date_trunc('year', tstz_year) = tstz_year),
    PRIMARY KEY (id)
);

CREATE TABLE mixed_checks (
    id            integer NOT NULL,
    billing_month date          NOT NULL CHECK (date_trunc('month', billing_month) = billing_month),
    posted_at     timestamptz   NOT NULL CHECK (EXTRACT(day FROM posted_at) = 1),
    state         ticket_state  NOT NULL,
    rating        integer       CHECK (rating BETWEEN 1 AND 5),
    priority      integer       CHECK (priority IN (1, 2, 3)),
    grade         varchar(2)    CHECK (grade IN ('A', 'B', 'C', 'D', 'F')),
    amount        numeric(8, 2) CHECK (amount >= 0 AND amount <= 9999.99),
    -- a function of the column that the parser must not misread: it is unsupported, so it is skipped
    -- with a warning and the column keeps its type default, which happens to satisfy it
    code          varchar(20)   CHECK (length(code) >= 0 AND length(code) <= 20),
    PRIMARY KEY (id)
);

-- TruncatedDateGenerator.set -> get round trip on each column type. No CHECK: it is about the driver.
CREATE TABLE round_trip (
    id   integer NOT NULL,
    d    date,
    ts   timestamp,
    tstz timestamp with time zone,
    PRIMARY KEY (id)
);
