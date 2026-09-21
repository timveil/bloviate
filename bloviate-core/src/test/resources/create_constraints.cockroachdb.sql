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

-- Issue #633: the same constraint conformance CockroachDB stores in its own spellings —
-- BETWEEN kept verbatim (PostgreSQL expands it), extract() written with a comma rather than FROM,
-- and ::STRING casts on the literals. One table per group so the tests can assert them separately.

CREATE TYPE order_status AS ENUM ('NEW', 'PAID', 'SHIPPED', 'CANCELLED');

CREATE TABLE constrained (
    id       int PRIMARY KEY,
    status   order_status NOT NULL,
    rating   int CHECK (rating BETWEEN 1 AND 5),
    priority int CHECK (priority IN (1, 2, 3)),
    grade    varchar(2) CHECK (grade IN ('A', 'B', 'C', 'D', 'F')),
    amount   decimal(8, 2) CHECK (amount >= 0 AND amount <= 9999.99),
    score    float CHECK (score >= 0 AND score <= 100)
);

CREATE TABLE month_dates (
    id           int PRIMARY KEY,
    d_trunc      date        NOT NULL CHECK (date_trunc('month', d_trunc) = d_trunc),
    d_extract    date        NOT NULL CHECK (EXTRACT(day FROM d_extract) = 1),
    ts_trunc     timestamp   NOT NULL CHECK (date_trunc('month', ts_trunc) = ts_trunc),
    tstz_trunc   timestamptz NOT NULL CHECK (date_trunc('month', tstz_trunc) = tstz_trunc),
    d_quarter    date        NOT NULL CHECK (date_trunc('quarter', d_quarter) = d_quarter),
    tstz_year    timestamptz NOT NULL CHECK (date_trunc('year', tstz_year) = tstz_year)
);

-- A function of the column: unsupported, so it is skipped with a warning and the column keeps its
-- type default, which happens to satisfy it. The fill must still succeed.
CREATE TABLE unsupported_check (
    id   int PRIMARY KEY,
    code varchar(20) CHECK (length(code) >= 0 AND length(code) <= 20)
);
