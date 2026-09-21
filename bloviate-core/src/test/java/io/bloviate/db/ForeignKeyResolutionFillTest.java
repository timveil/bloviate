/*
 * Copyright (c) 2021 Tim Veil
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.bloviate.db;

import io.bloviate.ext.H2Support;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #617: which key a foreign key is filled from. Foreign-key columns used to be matched to the
 * parent's <em>declared primary key by position</em>, which is right only for the ordinary
 * {@code references parent (id)} case. A key to a {@code UNIQUE} target, or to primary-key columns in
 * another order, was filled from the wrong column; a column shared by two keys was filled from
 * whichever the driver reported first, so the other key had nothing to match; and the wrap limit that
 * keeps a child inside its parent's key space was only set when the parent had a
 * {@link TableConfiguration} of its own.
 *
 * <p>Every case here fills against in-memory H2 with foreign keys enforced, so it runs without Docker
 * and a wrong value is a failed insert rather than an assertion nobody notices. The same shapes on a
 * real PostgreSQL are in {@link PostgresForeignKeyShapesTest}; the resolution rules themselves are
 * unit-tested in {@link ForeignKeyPlanTest}.
 */
class ForeignKeyResolutionFillTest {

    private static final int ROWS = 10;

    /**
     * The tenant pattern from the issue: {@code accounts} is keyed by {@code id} but also carries
     * {@code UNIQUE (tenant_id, id)}, and {@code documents} references that unique key. Positional
     * matching paired {@code documents.tenant_id} with {@code accounts.id} — the parent's first
     * primary-key column — and filled it with account keys. On top of that {@code documents.tenant_id}
     * belongs to two keys at once, so it has to satisfy both.
     */
    @Test
    void aCompositeKeyToAUniqueTargetIsFilledFromTheColumnsItNames() throws SQLException {
        String url = fresh("tenant", """
                create table tenants (id int primary key, name varchar(20) not null);
                create table accounts (
                    id int primary key,
                    tenant_id int not null references tenants (id),
                    label varchar(20) not null,
                    constraint uq_accounts_tenant unique (tenant_id, id));
                create table documents (
                    id int primary key,
                    tenant_id int not null,
                    account_id int not null,
                    constraint fk_documents_account foreign key (tenant_id, account_id) references accounts (tenant_id, id),
                    constraint fk_documents_tenant foreign key (tenant_id) references tenants (id));
                """);

        try (Connection connection = DriverManager.getConnection(url)) {
            new DatabaseFiller.Builder(connection, configuration(null)).build().fill();

            assertEquals(ROWS, count(connection, "documents"));
            // every document's (tenant_id, account_id) is a real account row, and its tenant_id a real
            // tenant — both keys hold at once, which is what a shared column has to achieve
            assertEquals(0, count(connection, """
                    select count(*) from documents d
                     where not exists (select 1 from accounts a where a.tenant_id = d.tenant_id and a.id = d.account_id)
                        or not exists (select 1 from tenants t where t.id = d.tenant_id)"""));
        }
    }

    /**
     * The same shape with more children than parents: the wrap limit has to come from the whole chain,
     * so {@code documents} cycles through the accounts and tenants that exist.
     */
    @Test
    void aChildLargerThanItsParentsStaysInsideBothKeySpaces() throws SQLException {
        String url = fresh("tenant_wide", """
                create table tenants (id int primary key, name varchar(20) not null);
                create table accounts (
                    id int primary key,
                    tenant_id int not null references tenants (id),
                    label varchar(20) not null,
                    constraint uq_accounts_tenant unique (tenant_id, id));
                create table documents (
                    id int primary key,
                    tenant_id int not null,
                    account_id int not null,
                    constraint fk_documents_account foreign key (tenant_id, account_id) references accounts (tenant_id, id),
                    constraint fk_documents_tenant foreign key (tenant_id) references tenants (id));
                """);

        try (Connection connection = DriverManager.getConnection(url)) {
            new DatabaseFiller.Builder(connection, configuration(Set.of(new TableConfiguration("documents", 50))))
                    .build().fill();

            assertEquals(ROWS, count(connection, "accounts"));
            assertEquals(50, count(connection, "documents"));
        }
    }

    /**
     * The same shape with row counts that are not multiples of each other: 6 tenants, 10 accounts, 50
     * documents. Each column's index has to be reduced by its parent's count <em>and then</em> by its
     * parent's own parent's, in that order. Reducing each column by the smallest count in its chain
     * instead puts {@code tenant_id} and {@code account_id} on different account rows — document 13
     * would pair tenant 1 with account 3, which is not a row of {@code accounts}.
     */
    @Test
    void aCompositeKeyStaysOnOneParentRowWhenTheCountsAreNotMultiples() throws SQLException {
        String url = fresh("tenant_uneven", """
                create table tenants (id int primary key, name varchar(20) not null);
                create table accounts (
                    id int primary key,
                    tenant_id int not null references tenants (id),
                    label varchar(20) not null,
                    constraint uq_accounts_tenant unique (tenant_id, id));
                create table documents (
                    id int primary key,
                    tenant_id int not null,
                    account_id int not null,
                    constraint fk_documents_account foreign key (tenant_id, account_id) references accounts (tenant_id, id));
                """);

        try (Connection connection = DriverManager.getConnection(url)) {
            new DatabaseFiller.Builder(connection, configuration(Set.of(
                    new TableConfiguration("tenants", 6),
                    new TableConfiguration("accounts", 10),
                    new TableConfiguration("documents", 50)))).build().fill();

            assertEquals(50, count(connection, "documents"));
            assertEquals(0, count(connection, """
                    select count(*) from documents d
                     where not exists (select 1 from accounts a where a.tenant_id = d.tenant_id and a.id = d.account_id)"""));
        }
    }

    /**
     * One column in two <em>composite</em> keys, each to a composite primary key. Positional matching is
     * right here, so only sharing is in question: {@code tenant_id} used to be filled from whichever key
     * the driver reported first, leaving the other with nothing to match. The two parents' tenant
     * columns now carry the same values, so a shipment's tenant is a row of both.
     */
    @Test
    void aColumnInTwoCompositeKeysSatisfiesBothParents() throws SQLException {
        String url = fresh("tenant_shared", """
                create table regions (tenant_id int not null, id bigint not null, primary key (tenant_id, id));
                create table warehouses (tenant_id int not null, id bigint not null, primary key (tenant_id, id));
                create table shipments (
                    id bigint primary key,
                    tenant_id int not null,
                    region_id bigint not null,
                    warehouse_id bigint not null,
                    constraint fk_shipments_region foreign key (tenant_id, region_id) references regions (tenant_id, id),
                    constraint fk_shipments_warehouse foreign key (tenant_id, warehouse_id) references warehouses (tenant_id, id));
                """);

        try (Connection connection = DriverManager.getConnection(url)) {
            new DatabaseFiller.Builder(connection, configuration(null)).build().fill();

            assertEquals(ROWS, count(connection, "regions"));
            assertEquals(ROWS, count(connection, "warehouses"));
            assertEquals(ROWS, count(connection, "shipments"));
            assertEquals(0, count(connection, """
                    select count(*) from shipments s
                     where not exists (select 1 from regions r where r.tenant_id = s.tenant_id and r.id = s.region_id)
                        or not exists (select 1 from warehouses w where w.tenant_id = s.tenant_id and w.id = s.warehouse_id)"""));
        }
    }

    /**
     * A key naming a single {@code UNIQUE} column that is not the parent's first primary-key column.
     * Positional matching filled {@code b_ref} with {@code two_part.a} values, which need not appear in
     * {@code two_part.b} at all.
     */
    @Test
    void aKeyToAUniqueColumnIsNotFilledFromTheFirstPrimaryKeyColumn() throws SQLException {
        String url = fresh("unique_target", """
                create table two_part (a int not null, b int not null, primary key (a, b), constraint uq_two_part_b unique (b));
                create table refs_b (id int primary key, b_ref int not null references two_part (b));
                """);

        try (Connection connection = DriverManager.getConnection(url)) {
            new DatabaseFiller.Builder(connection, configuration(null)).build().fill();

            assertEquals(ROWS, count(connection, "refs_b"));
            assertEquals(0, count(connection, "select count(*) from refs_b r where r.b_ref not in (select b from two_part)"));
            // and it is genuinely the b column, not a that happens to match
            assertTrue(count(connection, "select count(*) from two_part where a <> b") > 0,
                    "the fixture must give a and b different values, or the test proves nothing");
        }
    }

    /**
     * A key to a column the database generates. The parent's key is left out of its own insert — the
     * database assigns 1..N — so the child cannot share a seed with it and counts instead.
     */
    @Test
    void aKeyToAGeneratedColumnIsFilledWithTheValuesTheDatabaseAssigns() throws SQLException {
        String url = fresh("identity", """
                create table authors (id int generated always as identity primary key, name varchar(20) not null);
                create table books (
                    id int generated always as identity primary key,
                    author_id int not null references authors (id),
                    title varchar(20) not null);
                """);

        try (Connection connection = DriverManager.getConnection(url)) {
            new DatabaseFiller.Builder(connection, configuration(null)).build().fill();

            assertEquals(ROWS, count(connection, "authors"));
            assertEquals(ROWS, count(connection, "books"));
            assertEquals(0, count(connection, "select count(*) from books b where b.author_id not in (select id from authors)"));
        }
    }

    /**
     * A column referencing a generated key <em>and</em> an ordinary one. The generated key forces the
     * whole class to count rather than share a seed, so the ordinary key is filled 1..N as well;
     * counting only the shared column would leave the ordinary key on random values with nothing to
     * match.
     */
    @Test
    void aColumnReferencingAGeneratedKeyAndAnOrdinaryOneSatisfiesBoth() throws SQLException {
        String url = fresh("mixed_generated", """
                create table generated_parent (id int generated always as identity primary key, name varchar(20) not null);
                create table ordinary_parent (code int primary key, name varchar(20) not null);
                create table bridge (
                    id int primary key,
                    ref int not null,
                    constraint fk_bridge_generated foreign key (ref) references generated_parent (id),
                    constraint fk_bridge_ordinary foreign key (ref) references ordinary_parent (code));
                """);

        try (Connection connection = DriverManager.getConnection(url)) {
            new DatabaseFiller.Builder(connection, configuration(null)).build().fill();

            assertEquals(ROWS, count(connection, "bridge"));
            assertEquals(0, count(connection, """
                    select count(*) from bridge b
                     where b.ref not in (select id from generated_parent)
                        or b.ref not in (select code from ordinary_parent)"""));
        }
    }

    /**
     * The wrap limit used to be set only from an explicit {@link TableConfiguration} on the parent, so
     * a child sized past an unconfigured parent ran off the end of its key space.
     */
    @Test
    void theWrapLimitFallsBackToTheDefaultRowCountForAnUnconfiguredParent() throws SQLException {
        String url = fresh("cardinality", """
                create table parents (id int primary key, name varchar(20) not null);
                create table children (id int primary key, parent_id int not null references parents (id));
                """);

        try (Connection connection = DriverManager.getConnection(url)) {
            new DatabaseFiller.Builder(connection, configuration(Set.of(new TableConfiguration("children", 100))))
                    .build().fill();

            assertEquals(ROWS, count(connection, "parents"));
            assertEquals(100, count(connection, "children"));
        }
    }

    // ---------------------------------------------------------------------------------------------

    private static String fresh(String name, String schema) throws SQLException {
        String url = "jdbc:h2:mem:fk_" + name + "_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url)) {
            SqlScriptRunner.run(connection, SqlScript.inline("schema", schema));
        }
        return url;
    }

    private static DatabaseConfiguration configuration(Set<TableConfiguration> tables) {
        return new DatabaseConfiguration(16, ROWS, new H2Support(), tables, 42L);
    }

    private static long count(Connection connection, String tableOrQuery) throws SQLException {
        String sql = tableOrQuery.contains(" ") ? tableOrQuery : "select count(*) from " + tableOrQuery;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
