CREATE TABLE array_table
(
    id uuid PRIMARY KEY,
    a  string array,
    b  string[],
    c  int array,
    d  int[]
);

CREATE TABLE bit_table
(
    id uuid PRIMARY KEY,
    a  bit,
    b  bit(3),
    c  varbit,
    d  varbit(3)
);

CREATE TABLE bool_table
(
    id uuid PRIMARY KEY,
    a  bool,
    b  boolean
);

CREATE TABLE bytes_table
(
    id uuid PRIMARY KEY,
    a  bytes,
    b  bytea,
    c  blob
);

CREATE TABLE date_table
(
    id uuid PRIMARY KEY,
    a  date
);

CREATE TABLE decimal_table
(
    id uuid PRIMARY KEY,
    a  decimal,
    b  decimal(10, 2),
    c  numeric,
    d  numeric(10, 5),
    e  dec
);

CREATE TABLE float_table
(
    id uuid PRIMARY KEY,
    a  float,
    b  real,
    c  double precision
);

CREATE TABLE inet_table
(
    id uuid PRIMARY KEY,
    a  inet
);

CREATE TABLE interval_table
(
    id uuid PRIMARY KEY,
    a  INTERVAL
);

CREATE TABLE string_table
(
    id uuid PRIMARY KEY,
    a  string,
    --b  string(4), --string(n) is a problem for us as we don't report or respect length
    c  text,
    d  varchar,
    e  char,
    f  character,
    g  char(5),
    h  varchar(100)
);

CREATE TABLE int_table
(
    id uuid PRIMARY KEY,
    a  int,
    b  smallint,
    c  int4,
    d  int8,
    e  bigint
);

CREATE TABLE time_table
(
    id uuid PRIMARY KEY,
    a  time,
    b  time WITHOUT TIME ZONE
);

CREATE TABLE timestamp_table
(
    id uuid PRIMARY KEY,
    a  timestamp,
    b  timestamptz
);

CREATE TABLE jsonb_table
(
    id uuid PRIMARY KEY,
    a  jsonb
);