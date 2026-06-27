CREATE TABLE uuid_table (
    id uuid PRIMARY KEY,
    a  uuid
);

CREATE TABLE json_table (
    id uuid PRIMARY KEY,
    a  json,
    b  jsonb
);

CREATE TABLE network_table (
    id uuid PRIMARY KEY,
    a  inet,
    b  cidr,
    c  macaddr,
    d  macaddr8
);

CREATE TABLE bit_table (
    id uuid PRIMARY KEY,
    a  bit,
    b  bit(3),
    c  bit varying,
    d  bit varying(3)
);

CREATE TABLE interval_table (
    id uuid PRIMARY KEY,
    a  interval
);

CREATE TABLE array_table (
    id uuid PRIMARY KEY,
    a  text[],
    b  integer[],
    c  bigint[]
);

CREATE TABLE doc_table (
    id uuid PRIMARY KEY,
    a  xml
);

CREATE TABLE standard_table (
    id uuid PRIMARY KEY,
    a  smallint,
    b  integer,
    c  bigint,
    d  real,
    e  double precision,
    f  numeric(10, 2),
    g  varchar(50),
    h  char(5),
    i  text,
    j  boolean,
    k  date,
    l  time,
    m  timestamp,
    n  timestamptz,
    o  bytea
);
