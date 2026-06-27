-- NB: "json_table" is reserved (the JSON_TABLE() function), so this is named "json_doc"
CREATE TABLE json_doc
(
    id INT NOT NULL AUTO_INCREMENT,
    a  JSON,
    PRIMARY KEY (id)
);

CREATE TABLE standard_table
(
    id INT NOT NULL AUTO_INCREMENT,
    a  TINYINT UNSIGNED, -- the default TINYINT generator produces 0..255 (unsigned range)
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
