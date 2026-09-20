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

-- Fixture for issue #615: the shapes of declarative partitioning PostgreSQL offers, one schema each so a
-- failure in one cannot mask another (see PostgresPartitionShapesTest). The default value ranges the
-- engine generates (about 2020, +-100 days) fall inside a partition only where a schema says so.

-- LIST partitioning by a text column, no default partition: only 'eu' and 'us' are accepted.
CREATE SCHEMA part_list;

CREATE TABLE part_list.events (
    id      bigint      NOT NULL,
    region  varchar(10) NOT NULL,
    payload varchar(50),
    PRIMARY KEY (id, region)
) PARTITION BY LIST (region);

CREATE TABLE part_list.events_eu PARTITION OF part_list.events FOR VALUES IN ('eu');
CREATE TABLE part_list.events_us PARTITION OF part_list.events FOR VALUES IN ('us');

-- HASH partitioning: every key value lands in some partition, so nothing needs configuring.
CREATE SCHEMA part_hash;

CREATE TABLE part_hash.accounts (
    id   bigint      NOT NULL,
    name varchar(50) NOT NULL,
    PRIMARY KEY (id)
) PARTITION BY HASH (id);

CREATE TABLE part_hash.accounts_0 PARTITION OF part_hash.accounts FOR VALUES WITH (MODULUS 4, REMAINDER 0);
CREATE TABLE part_hash.accounts_1 PARTITION OF part_hash.accounts FOR VALUES WITH (MODULUS 4, REMAINDER 1);
CREATE TABLE part_hash.accounts_2 PARTITION OF part_hash.accounts FOR VALUES WITH (MODULUS 4, REMAINDER 2);
CREATE TABLE part_hash.accounts_3 PARTITION OF part_hash.accounts FOR VALUES WITH (MODULUS 4, REMAINDER 3);

-- RANGE partitioning with a DEFAULT partition: a value outside the named partition lands in the default
-- one, so an unconfigured fill (2020 dates) succeeds and fills only the default partition.
CREATE SCHEMA part_default;

CREATE TABLE part_default.logs (
    id        bigint      NOT NULL,
    logged_at timestamp   NOT NULL,
    message   varchar(50) NOT NULL,
    PRIMARY KEY (id, logged_at)
) PARTITION BY RANGE (logged_at);

CREATE TABLE part_default.logs_2024 PARTITION OF part_default.logs FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');
CREATE TABLE part_default.logs_other PARTITION OF part_default.logs DEFAULT;

-- Multi-level partitioning: by range on time, each year by list on site, one site further by hash. Only
-- the top-level table is filled; the intermediate partitions (which the driver lists as PARTITIONED
-- TABLEs) and the leaves are not.
CREATE SCHEMA part_multi;

CREATE TABLE part_multi.metrics (
    id       bigint    NOT NULL,
    taken_at timestamp NOT NULL,
    site     integer   NOT NULL,
    PRIMARY KEY (id, taken_at, site)
) PARTITION BY RANGE (taken_at);

CREATE TABLE part_multi.metrics_2024 PARTITION OF part_multi.metrics FOR VALUES FROM ('2024-01-01') TO ('2025-01-01')
    PARTITION BY LIST (site);
CREATE TABLE part_multi.metrics_2024_a PARTITION OF part_multi.metrics_2024 FOR VALUES IN (1);
CREATE TABLE part_multi.metrics_2024_b PARTITION OF part_multi.metrics_2024 FOR VALUES IN (2)
    PARTITION BY HASH (id);
CREATE TABLE part_multi.metrics_2024_b_0 PARTITION OF part_multi.metrics_2024_b FOR VALUES WITH (MODULUS 2, REMAINDER 0);
CREATE TABLE part_multi.metrics_2024_b_1 PARTITION OF part_multi.metrics_2024_b FOR VALUES WITH (MODULUS 2, REMAINDER 1);

-- A partitioned table with a foreign key to a plain table (a partitioned table can be the child of a
-- relationship), and a foreign key from a plain table to a partitioned table (and its partitions), the
-- partitions covering the default value range (2019 through 2021) so nothing needs configuring.
CREATE SCHEMA part_rel;

CREATE TABLE part_rel.customers (
    id   bigint       NOT NULL,
    name varchar(100) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE part_rel.invoices (
    id          bigint    NOT NULL,
    customer_id bigint    NOT NULL,
    issued_at   timestamp NOT NULL,
    PRIMARY KEY (id, issued_at),
    FOREIGN KEY (customer_id) REFERENCES part_rel.customers (id)
) PARTITION BY RANGE (issued_at);

CREATE TABLE part_rel.invoices_2019_2020 PARTITION OF part_rel.invoices FOR VALUES FROM ('2019-01-01') TO ('2020-06-01');
CREATE TABLE part_rel.invoices_2020_2021 PARTITION OF part_rel.invoices FOR VALUES FROM ('2020-06-01') TO ('2022-01-01');

CREATE TABLE part_rel.payments (
    id         bigint    NOT NULL,
    invoice_id bigint    NOT NULL,
    issued_at  timestamp NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (invoice_id, issued_at) REFERENCES part_rel.invoices (id, issued_at)
);
