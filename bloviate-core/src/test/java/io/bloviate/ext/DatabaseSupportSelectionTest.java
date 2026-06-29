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

package io.bloviate.ext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DatabaseSupportSelectionTest {

    @Test
    void selectsPostgresByProductName() {
        assertInstanceOf(PostgresSupport.class, DatabaseSupport.forProduct("PostgreSQL"));
    }

    @Test
    void selectsMySqlByProductName() {
        assertInstanceOf(MySQLSupport.class, DatabaseSupport.forProduct("MySQL"));
    }

    @Test
    void selectsCockroachByProductName() {
        assertInstanceOf(CockroachDBSupport.class, DatabaseSupport.forProduct("CockroachDB CCL v23.1.0"));
    }

    @Test
    void selectsMariaDbByProductName() {
        assertInstanceOf(MariaDBSupport.class, DatabaseSupport.forProduct("MariaDB"));
    }

    @Test
    void mariaDbIsAMySqlSupport() {
        // MariaDBSupport extends MySQLSupport, so the legacy MySQL driver reporting "MySQL"
        // against a MariaDB server still resolves to compatible (MySQL) type handling.
        assertInstanceOf(MySQLSupport.class, DatabaseSupport.forProduct("MariaDB"));
    }

    @Test
    void selectsH2ByProductName() {
        assertInstanceOf(H2Support.class, DatabaseSupport.forProduct("H2"));
    }

    @Test
    void selectsSqliteByProductName() {
        assertInstanceOf(SQLiteSupport.class, DatabaseSupport.forProduct("SQLite"));
    }

    @Test
    void matchingIsCaseInsensitive() {
        assertInstanceOf(MySQLSupport.class, DatabaseSupport.forProduct("mysql"));
        assertInstanceOf(PostgresSupport.class, DatabaseSupport.forProduct("POSTGRESQL"));
        assertInstanceOf(MariaDBSupport.class, DatabaseSupport.forProduct("mariadb"));
        assertInstanceOf(SQLiteSupport.class, DatabaseSupport.forProduct("SQLITE"));
    }

    @Test
    void fallsBackToDefaultForUnknownOrNull() {
        assertInstanceOf(DefaultSupport.class, DatabaseSupport.forProduct("Oracle"));
        assertInstanceOf(DefaultSupport.class, DatabaseSupport.forProduct(null));
        assertInstanceOf(DefaultSupport.class, DatabaseSupport.forProduct(""));
    }

    @Test
    void cockroachReportedAsPostgresResolvesToPostgres() {
        // documented caveat: CRDB via the pgjdbc driver reports "PostgreSQL", so auto-selection
        // cannot distinguish it -- callers must pass CockroachDBSupport explicitly.
        assertInstanceOf(PostgresSupport.class, DatabaseSupport.forProduct("PostgreSQL"));
    }
}
