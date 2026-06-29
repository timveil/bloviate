-- Comprehensive MariaDB type coverage. "standard_table" fills with zero configuration via the
-- MySQL-inherited support. MariaDB's JSON type is an alias for LONGTEXT and surfaces through
-- DatabaseMetaData.getColumns() as LONGVARCHAR/LONGTEXT (indistinguishable from plain TEXT), yet it
-- carries an automatic CHECK (json_valid(...)) -- so "json_doc.a" must be filled via a per-column
-- JsonbGenerator override (see MariaDbFillerTest); a random string would violate the constraint.

CREATE TABLE standard_table
(
    id INT NOT NULL AUTO_INCREMENT,
    a  TINYINT UNSIGNED, -- JDBC TINYINT; the inherited 0..255 generator fits the unsigned range
    b  SMALLINT,
    c  INT,
    d  BIGINT,
    e  DECIMAL(10, 2),
    f  FLOAT,
    g  DOUBLE,
    h  VARCHAR(50),
    i  CHAR(5),
    j  TEXT,
    k  BOOLEAN,
    l  DATE,
    m  TIME,
    n  DATETIME,
    o  BLOB,
    p  VARBINARY(50),
    PRIMARY KEY (id)
);

CREATE TABLE json_doc
(
    id INT NOT NULL AUTO_INCREMENT,
    a  JSON,
    PRIMARY KEY (id)
);
