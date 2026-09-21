-- Integer-width coverage for MySQL and MariaDB; the DDL is identical on both servers, so one
-- fixture serves MySqlFillerTest and MariaDbFillerTest.
--
-- Two families of column that standard JDBC metadata describes only indirectly:
--
--  * BIT(n) holds an n-bit unsigned integer on MySQL, not the bit string the SQL standard (and
--    PostgreSQL) define. A generated bit string is read as that many bytes and rejected with
--    "Data too long" for every n above 1.
--  * TINYINT is signed (-128..127) unless declared UNSIGNED (0..255), and JDBC metadata carries
--    no signedness column, so only the type name says which.
--
-- Every column is NOT NULL, so the fill has to produce a value for each one.

CREATE TABLE numeric_widths
(
    id            INT              NOT NULL AUTO_INCREMENT,
    bit_1         BIT(1)           NOT NULL,
    bit_3         BIT(3)           NOT NULL,
    bit_8         BIT(8)           NOT NULL,
    bit_17        BIT(17)          NOT NULL,
    bit_64        BIT(64)          NOT NULL,
    tiny_signed   TINYINT          NOT NULL,
    tiny_unsigned TINYINT UNSIGNED NOT NULL,
    small_signed  SMALLINT         NOT NULL,
    bool_flag     BOOLEAN          NOT NULL,
    PRIMARY KEY (id)
);
