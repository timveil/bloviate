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

-- Fixture for issue #614 (part of #613): a multi-tenant SaaS-style schema with a range-partitioned
-- table, tenant-scoped composite foreign keys and date CHECK constraints, plus one small schema per
-- scenario so that a failure in one cannot mask another. Each test connects with a pool whose
-- current schema is the scenario schema (see PostgresSchemaFixture), because the engine introspects
-- only the connection's current schema.
--
-- The scenario schemas that are not about partitioning avoid partitioned tables entirely, and vice
-- versa, for the same reason.

-- ---------------------------------------------------------------------------------------------
-- saas: the whole motivating schema from #613 in one place (end-to-end target for the umbrella).
-- ---------------------------------------------------------------------------------------------
CREATE SCHEMA saas;

-- tenant-scoped parent: PRIMARY KEY (id) plus UNIQUE (tenant_id, id) so children can carry a
-- composite FK that also pins the tenant
CREATE TABLE saas.customers (
    id        bigint       NOT NULL,
    tenant_id integer      NOT NULL,
    name      varchar(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, id)
);

CREATE TABLE saas.projects (
    id          bigint       NOT NULL,
    tenant_id   integer      NOT NULL,
    customer_id bigint       NOT NULL,
    name        varchar(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, customer_id) REFERENCES saas.customers (tenant_id, id)
);

-- tenant_id is shared by two composite foreign keys
CREATE TABLE saas.tasks (
    id          bigint       NOT NULL,
    tenant_id   integer      NOT NULL,
    project_id  bigint       NOT NULL,
    customer_id bigint       NOT NULL,
    title       varchar(100) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id, project_id)  REFERENCES saas.projects (tenant_id, id),
    FOREIGN KEY (tenant_id, customer_id) REFERENCES saas.customers (tenant_id, id)
);

-- range-partitioned by time, with leaf partitions covering a bounded range (2024-01-01, 2024-04-01)
CREATE TABLE saas.orders (
    id        bigint        NOT NULL,
    tenant_id integer       NOT NULL,
    placed_at timestamp     NOT NULL,
    total     numeric(10,2) NOT NULL,
    PRIMARY KEY (id, placed_at)
) PARTITION BY RANGE (placed_at);

CREATE TABLE saas.orders_2024_01 PARTITION OF saas.orders FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
CREATE TABLE saas.orders_2024_02 PARTITION OF saas.orders FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');
CREATE TABLE saas.orders_2024_03 PARTITION OF saas.orders FOR VALUES FROM ('2024-03-01') TO ('2024-04-01');

-- first-of-month dates, in the two spellings the checks below are written in
CREATE TABLE saas.invoices (
    id            integer NOT NULL,
    tenant_id     integer NOT NULL,
    billing_month date    NOT NULL,
    period_start  date    NOT NULL,
    PRIMARY KEY (id),
    CHECK (date_trunc('month', billing_month) = billing_month),
    CHECK (EXTRACT(day FROM period_start) = 1)
);

-- a rollup that is derived from orders; only present in the schema (there is no engine support)
CREATE TABLE saas.order_stats (
    tenant_id   integer       NOT NULL,
    stat_month  date          NOT NULL,
    order_count integer       NOT NULL,
    total       numeric(12,2) NOT NULL,
    PRIMARY KEY (tenant_id, stat_month)
);

-- ---------------------------------------------------------------------------------------------
-- orders_part: a partitioned table with no foreign keys at all (claim: leaf partitions are filled
-- directly, the partitioned parent is skipped).
-- ---------------------------------------------------------------------------------------------
CREATE SCHEMA orders_part;

CREATE TABLE orders_part.orders (
    id        bigint        NOT NULL,
    tenant_id integer       NOT NULL,
    placed_at timestamp     NOT NULL,
    total     numeric(10,2) NOT NULL,
    PRIMARY KEY (id, placed_at)
) PARTITION BY RANGE (placed_at);

CREATE TABLE orders_part.orders_2024_01 PARTITION OF orders_part.orders FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
CREATE TABLE orders_part.orders_2024_02 PARTITION OF orders_part.orders FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');
CREATE TABLE orders_part.orders_2024_03 PARTITION OF orders_part.orders FOR VALUES FROM ('2024-03-01') TO ('2024-04-01');

CREATE TABLE orders_part.order_stats (
    tenant_id   integer       NOT NULL,
    stat_month  date          NOT NULL,
    order_count integer       NOT NULL,
    total       numeric(12,2) NOT NULL,
    PRIMARY KEY (tenant_id, stat_month)
);

-- ---------------------------------------------------------------------------------------------
-- fk_to_part: a foreign key that references a partitioned table (PostgreSQL 12+).
-- ---------------------------------------------------------------------------------------------
CREATE SCHEMA fk_to_part;

CREATE TABLE fk_to_part.orders (
    id        bigint    NOT NULL,
    placed_at timestamp NOT NULL,
    PRIMARY KEY (id, placed_at)
) PARTITION BY RANGE (placed_at);

CREATE TABLE fk_to_part.orders_2024_01 PARTITION OF fk_to_part.orders FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
CREATE TABLE fk_to_part.orders_2024_02 PARTITION OF fk_to_part.orders FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');

CREATE TABLE fk_to_part.order_items (
    id        bigint    NOT NULL,
    order_id  bigint    NOT NULL,
    placed_at timestamp NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (order_id, placed_at) REFERENCES fk_to_part.orders (id, placed_at)
);

-- ---------------------------------------------------------------------------------------------
-- tenant_unique: composite FK that references UNIQUE (tenant_id, id), not the primary key (id).
-- ---------------------------------------------------------------------------------------------
CREATE SCHEMA tenant_unique;

CREATE TABLE tenant_unique.customers (
    id        bigint       NOT NULL,
    tenant_id integer      NOT NULL,
    name      varchar(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, id)
);

CREATE TABLE tenant_unique.projects (
    id          bigint       NOT NULL,
    tenant_id   integer      NOT NULL,
    customer_id bigint       NOT NULL,
    name        varchar(100) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id, customer_id) REFERENCES tenant_unique.customers (tenant_id, id)
);

-- ---------------------------------------------------------------------------------------------
-- tenant_shared: one column (tenant_id) shared by two composite FKs, both of which reference the
-- parents' composite PRIMARY KEY, so positional PK matching is right and only the shared column
-- is in question.
-- ---------------------------------------------------------------------------------------------
CREATE SCHEMA tenant_shared;

CREATE TABLE tenant_shared.regions (
    tenant_id integer NOT NULL,
    id        bigint  NOT NULL,
    PRIMARY KEY (tenant_id, id)
);

CREATE TABLE tenant_shared.warehouses (
    tenant_id integer NOT NULL,
    id        bigint  NOT NULL,
    PRIMARY KEY (tenant_id, id)
);

CREATE TABLE tenant_shared.shipments (
    id           bigint  NOT NULL,
    tenant_id    integer NOT NULL,
    region_id    bigint  NOT NULL,
    warehouse_id bigint  NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id, region_id)    REFERENCES tenant_shared.regions (tenant_id, id),
    FOREIGN KEY (tenant_id, warehouse_id) REFERENCES tenant_shared.warehouses (tenant_id, id)
);

-- ---------------------------------------------------------------------------------------------
-- composite_pk: control. A composite FK to a composite PRIMARY KEY is supported (TPC-C relies on it).
-- ---------------------------------------------------------------------------------------------
CREATE SCHEMA composite_pk;

CREATE TABLE composite_pk.parent (
    a integer NOT NULL,
    b integer NOT NULL,
    PRIMARY KEY (a, b)
);

CREATE TABLE composite_pk.child (
    id integer NOT NULL,
    a  integer NOT NULL,
    b  integer NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (a, b) REFERENCES composite_pk.parent (a, b)
);

-- ---------------------------------------------------------------------------------------------
-- check_trunc / check_extract: first-of-month date CHECKs, one schema per spelling.
-- ---------------------------------------------------------------------------------------------
CREATE SCHEMA check_trunc;

CREATE TABLE check_trunc.invoices (
    id            integer NOT NULL,
    billing_month date    NOT NULL,
    PRIMARY KEY (id),
    CHECK (date_trunc('month', billing_month) = billing_month)
);

CREATE SCHEMA check_extract;

CREATE TABLE check_extract.statements (
    id           integer NOT NULL,
    period_start date    NOT NULL,
    PRIMARY KEY (id),
    CHECK (EXTRACT(day FROM period_start) = 1)
);
