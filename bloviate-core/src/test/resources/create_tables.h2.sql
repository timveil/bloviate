-- Comprehensive H2 type coverage. Standard types map through the cross-database defaults; the
-- H2-specific UUID and JSON types are handled by H2Support. TINYINT is signed (-128..127) in H2.

CREATE TABLE standard_table
(
    id INT NOT NULL AUTO_INCREMENT,
    a  TINYINT,
    b  SMALLINT,
    c  INT,
    d  BIGINT,
    e  DECIMAL(10, 2),
    f  REAL,
    g  DOUBLE PRECISION,
    h  VARCHAR(50),
    i  CHAR(5),
    j  CLOB,
    k  BOOLEAN,
    l  DATE,
    m  TIME,
    n  TIMESTAMP,
    o  TIMESTAMP WITH TIME ZONE,
    p  BLOB,
    q  VARBINARY(50),
    PRIMARY KEY (id)
);

CREATE TABLE special_types
(
    id INT NOT NULL AUTO_INCREMENT,
    u  UUID,
    j  JSON,
    PRIMARY KEY (id)
);
