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

-- Issue #616: tables whose dates only admit one quarter, so a fill is valid only if its dates sit near a
-- pinned asOf (2026-04-01). timestamptz throughout, so the bound is an exact instant whatever zone the
-- JVM or the session runs in.

-- range CHECKs on a timestamptz and on a date
CREATE TABLE orders (
    id        integer                  NOT NULL PRIMARY KEY,
    placed_at timestamp with time zone NOT NULL,
    due_on    date                     NOT NULL,
    CONSTRAINT placed_in_q1 CHECK (placed_at >= '2026-01-01T00:00:00Z' AND placed_at < '2026-04-01T00:00:00Z'),
    CONSTRAINT due_in_q1    CHECK (due_on >= DATE '2026-01-01' AND due_on < DATE '2026-04-01')
);

-- a child of orders with a narrower CHECK, to show the foreign key and a second table sharing the anchor
CREATE TABLE order_notes (
    id       integer                  NOT NULL PRIMARY KEY,
    order_id integer                  NOT NULL REFERENCES orders (id),
    noted_at timestamp with time zone NOT NULL,
    CONSTRAINT noted_last_ten_days CHECK (noted_at >= '2026-03-22T00:00:00Z' AND noted_at < '2026-04-01T00:00:00Z')
);

-- a range-partitioned table with no DEFAULT partition: Bloviate fills it through the parent, so the partition key
-- has to fall inside a partition, which the test arranges with a window relative to a pinned asOf
CREATE TABLE ledger (
    id        integer                  NOT NULL,
    booked_at timestamp with time zone NOT NULL,
    PRIMARY KEY (id, booked_at)
) PARTITION BY RANGE (booked_at);

CREATE TABLE ledger_2025_q4 PARTITION OF ledger
    FOR VALUES FROM ('2025-10-01T00:00:00Z') TO ('2026-01-01T00:00:00Z');

CREATE TABLE ledger_2026_q1 PARTITION OF ledger
    FOR VALUES FROM ('2026-01-01T00:00:00Z') TO ('2026-04-01T00:00:00Z');

-- unconstrained tables, so a fill can be moved to any asOf
CREATE TABLE events (
    id          integer                  NOT NULL PRIMARY KEY,
    happened_at timestamp with time zone NOT NULL,
    happened_on date                     NOT NULL
);

CREATE TABLE audit (
    id        integer                  NOT NULL PRIMARY KEY,
    audited_at timestamp with time zone NOT NULL
);
