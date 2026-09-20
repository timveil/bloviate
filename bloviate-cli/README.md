# Bloviate CLI

`bloviate` fills the tables of any JDBC database with reproducible, foreign-key-consistent test data
from the shell. It is a thin command line over `bloviate-core`: every setting maps to something the
library already exposes.

The runnable jar is `bloviate-cli/target/bloviate-cli.jar` after `./mvnw package`. It is not published
to Maven (it will ship as a GitHub Release asset and a container image).

## Running it

No JDBC driver is bundled. Put the driver for your database on the classpath and start the main class
(`java -jar` ignores `-cp`, so it cannot be used to add a driver):

```bash
java -cp bloviate-cli.jar:postgresql-42.7.13.jar io.bloviate.cli.Main fill \
    --url 'jdbc:postgresql://localhost:5432/shop?stringtype=unspecified' --user shop \
    --rows 1000 --seed 42

java -jar bloviate-cli.jar --help          # top-level help, and the exit codes
java -jar bloviate-cli.jar fill --help     # every fill flag
java -jar bloviate-cli.jar --version
```

The password is never an argument (it would show up in the process list). Give it in the
`BLOVIATE_PASSWORD` environment variable or in a file (`--password-file`, which wins when both are set);
a password inside the JDBC URL works too and is masked in everything the CLI prints.

## `fill`

| Flag | Meaning | Default |
| --- | --- | --- |
| `--url <jdbc-url>` | JDBC URL of the database to fill (required) | |
| `--user <user>` | Database user, if not in the URL | |
| `--password-file <file>` | File holding the password | `BLOVIATE_PASSWORD` |
| `--seed <n>` | Base seed; the same seed on the same schema gives the same data | `0` |
| `--rows <n>` | Rows per table | `100` |
| `--table-rows <table=N>` | Rows for one table; repeatable or comma-separated. An unknown table is an error (see below) | |
| `--batch-size <n>` | Rows per JDBC batch insert | `1000` |
| `--threads <n>` | Worker threads; above 1 fills independent tables concurrently, one connection each | `1` |
| `--commit <mode>` | `connection-default`, `per-table` or `every-n-batches` | `connection-default` |
| `--commit-batches <n>` | Batches between commits; needed by, and only for, `every-n-batches` | |
| `--bulk-load <mode>` | `ordered`, or `unordered` (constraints off, every table at once; needs `--threads` above 1 and PostgreSQL or MySQL) | `ordered` |
| `--schema <schema>` | Schema to fill instead of the connection's current one | |
| `--catalog <catalog>` | Catalog to fill (the database, on MySQL and MariaDB) | |
| `--include <pattern>` | Fill only matching tables; repeatable or comma-separated. `*` matches any run of characters, `?` one character, case-insensitive. A pattern that matches nothing is an error | all tables |
| `--exclude <pattern>` | Leave matching tables unfilled, for example one an `--after` script computes. Same syntax | |
| `--before <file>` | SQL script to run before the fill, for example to truncate; repeatable, run in order | |
| `--after <file>` | SQL script to run after the fill, for example to compute a rollup; repeatable, run in order. A failing script stops the run | |
| `--support <database>` | `postgres`, `cockroachdb`, `mysql`, `mariadb`, `h2`, `sqlite`, `bigquery` or `default`, instead of detecting it from the connection | detected |
| `--no-batch-rewrite` | Do not add the driver's batch-rewrite parameter to the URL (below) | |
| `-v`, `--verbose` | Log at DEBUG and print a stack trace on failure | |
| `-q`, `--quiet` | Log warnings and errors only | INFO |

Logs go to stderr; stdout is left empty.

Script files are read as UTF-8 and run by `SqlScriptRunner` (statements split on `;`, dollar-quoting,
comments); see its Javadoc for the syntax and the transaction rules. A fill is not idempotent, so a
`--before` script that truncates the tables is what makes a re-run possible.

**Batch rewrite.** For `jdbc:postgresql:`, `jdbc:mysql:` and `jdbc:mariadb:` URLs that do not already
set it, the CLI appends the driver's batch-rewrite parameter (`reWriteBatchedInserts=true`,
`rewriteBatchedStatements=true`) and logs that it did, since it is often the biggest single speedup. A
URL that already has the parameter, with any value, is never touched. `--no-batch-rewrite` turns this off.

**PostgreSQL types.** Columns of types such as `uuid`, `json` or `interval` need
`stringtype=unspecified` in the URL, as in the example above.

**`--table-rows`.** Names are checked against the schema before anything is written and a name that
matches no table is a usage error. When `--before` scripts are given the check is skipped, because they
may create the tables; the engine then only warns. Give every table in a parent/child chain whose row
counts differ its own `--table-rows`: a child with more rows than a parent that has no explicit count
would reference parent keys that were never generated.

## Exit codes

| Code | Meaning |
| --- | --- |
| `0` | Success |
| `1` | Fill or hook failure: a SQL error while filling (the message names the table) or in a `--before`/`--after` script. Tables filled before the failure stay filled |
| `2` | Usage or configuration error: an unknown flag or bad value, a missing `--url`, a table pattern that matches nothing, a foreign key to an excluded table, an unknown `--table-rows` name |
| `3` | Connection error: the database cannot be reached, or no JDBC driver on the classpath accepts the URL |

A failure prints one `bloviate: <message>` line on stderr, with the driver's reason and no stack trace;
`-v` adds the stack trace.
