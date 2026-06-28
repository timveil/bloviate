# Database Support

Bloviate resolves cross-database defaults for the common JDBC types and adds handling for
vendor-specific types on the databases that need it. Pick a `DatabaseSupport` implementation
explicitly, or let Bloviate detect it from the connection.

| Database | Support Class | Coverage |
|----------|---------------|----------|
| PostgreSQL | `PostgresSupport` | Standard JDBC types **plus** PostgreSQL vendor types |
| CockroachDB | `CockroachDBSupport` | Extends `PostgresSupport` (CockroachDB is wire-compatible) |
| MySQL | `MySQLSupport` | Standard JDBC types **plus** `JSON` |
| Generic JDBC | `DefaultSupport` | Standard JDBC types only |

All four resolve the cross-database defaults for the common JDBC types (integers, decimals,
strings, dates/times, booleans, binary, …). `PostgresSupport` and `MySQLSupport` add handling for
vendor-specific types on top of those defaults.

## Vendor types

**PostgreSQL vendor types:** `uuid`, `json`, `jsonb`, `inet`, `cidr`, `macaddr`, `macaddr8`,
`interval`, `bit`/`bit varying`, `xml`, and `text`/`integer`/`bigint` arrays.

**MySQL vendor types:** `JSON` (generated as valid JSON rather than arbitrary text). `ENUM`, `SET`,
`GEOMETRY`, and `YEAR` are **not** supported — they need value-aware or binary generation that
standard JDBC metadata doesn't expose.

> **PostgreSQL connection requirement:** the vendor types above are bound as their text
> representations, and PostgreSQL won't implicitly cast `varchar` to `uuid`/`jsonb`/`bit`/etc.
> Open the connection with `stringtype=unspecified` so the server infers each column's type:
> `jdbc:postgresql://host/db?stringtype=unspecified`.

## Auto-detection

You can let Bloviate pick the support implementation from the connection's metadata instead of
hardcoding it:

```java
DatabaseSupport support = DatabaseSupport.forConnection(connection);
```

> **Note:** CockroachDB is reached through the PostgreSQL JDBC driver and reports its product name
> as `PostgreSQL`, so auto-selection resolves it to `PostgresSupport`. Because `CockroachDBSupport`
> extends `PostgresSupport` and adds no extra behavior, the two are equivalent for data generation.
