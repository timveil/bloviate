-- Comprehensive SQLite type coverage. SQLite uses dynamic typing with column affinity, so the
-- declared types below collapse (via DatabaseMetaData.getColumns) to a handful of JDBC types
-- (INTEGER / VARCHAR / FLOAT / BLOB). There are no native BOOLEAN/DATE/DATETIME types; those are
-- stored per SQLite convention and accept the generated values under affinity rules.

CREATE TABLE standard_table
(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    a  INTEGER,
    b  BIGINT,
    c  REAL,
    d  DOUBLE,
    e  NUMERIC,
    f  DECIMAL(10, 2),
    g  TEXT,
    h  VARCHAR(50),
    i  BLOB,
    j  BOOLEAN,
    k  DATE,
    l  DATETIME,
    m  TIMESTAMP
);
