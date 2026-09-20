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

-- Fixture for issue #614 (part of #613): small foreign-key shapes, one schema per scenario so a
-- failure in one cannot mask another. Companion to create_partitioned.postgres.sql. Each test
-- connects with a pool whose current schema is the scenario schema (see PostgresSchemaFixture).

-- a table whose foreign key references its own primary key (an org chart)
CREATE SCHEMA self_ref;

CREATE TABLE self_ref.employees (
    id         integer     NOT NULL,
    manager_id integer,
    name       varchar(50) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (manager_id) REFERENCES self_ref.employees (id)
);

-- two tables that reference each other; the cycle is only closable because one side is nullable
CREATE SCHEMA fk_cycle;

CREATE TABLE fk_cycle.accounts (
    id           integer NOT NULL,
    primary_user integer,
    PRIMARY KEY (id)
);

CREATE TABLE fk_cycle.users (
    id         integer NOT NULL,
    account_id integer NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (account_id) REFERENCES fk_cycle.accounts (id)
);

ALTER TABLE fk_cycle.accounts
    ADD CONSTRAINT accounts_primary_user_fk FOREIGN KEY (primary_user) REFERENCES fk_cycle.users (id);

-- foreign keys to auto-generated primary keys: serial, and identity
CREATE SCHEMA fk_identity;

CREATE TABLE fk_identity.authors (
    id   serial      PRIMARY KEY,
    name varchar(50) NOT NULL
);

CREATE TABLE fk_identity.books (
    id        serial      PRIMARY KEY,
    author_id integer     NOT NULL REFERENCES fk_identity.authors (id),
    title     varchar(50) NOT NULL
);

CREATE SCHEMA fk_identity_always;

CREATE TABLE fk_identity_always.publishers (
    id   integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name varchar(50) NOT NULL
);

CREATE TABLE fk_identity_always.imprints (
    id           integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    publisher_id integer     NOT NULL REFERENCES fk_identity_always.publishers (id),
    name         varchar(50) NOT NULL
);

-- an ordinary parent/child pair, used to size the child larger than a parent that has no row
-- count of its own
CREATE SCHEMA fk_cardinality;

CREATE TABLE fk_cardinality.parents (
    id   integer     NOT NULL,
    name varchar(50) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE fk_cardinality.children (
    id        integer NOT NULL,
    parent_id integer NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (parent_id) REFERENCES fk_cardinality.parents (id)
);

-- identical shape, kept apart so the failure-semantics tests cannot disturb the cardinality tests
CREATE SCHEMA partial_fail;

CREATE TABLE partial_fail.parents (
    id   integer     NOT NULL,
    name varchar(50) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE partial_fail.children (
    id        integer NOT NULL,
    parent_id integer NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (parent_id) REFERENCES partial_fail.parents (id)
);
