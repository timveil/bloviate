# Why Bloviate

> **Bloviate never touches production data, is deterministic by seed, and runs inside your
> JUnit/Testcontainers pipeline.**

It generates *from your schema*, not from a copy of production — so the privacy story is simple and
the data is reproducible by construction.

## Five structural strengths

1. **Zero-config foreign-key correctness.** It auto-introspects `DatabaseMetaData`, builds the FK
   dependency graph, and topologically orders the fill — parents before children, no configuration.
   Rivals make you hand-author relationships (Benerator `<reference>` selectors, Snowfakery YAML
   recipes) or require parent rows to already exist (DBeaver).
2. **Privacy-safe by construction.** Generated purely from schema and type — production data is
   never read — so the GDPR/CCPA exposure surface is structurally minimal. No masking or
   differential-privacy machinery to configure or trust.
3. **Deterministic reproducibility as a contract.** The same seed and schema produce byte-identical
   data on every run, including under parallel and partitioned fills. Most ML synthesizers can't
   offer that.
4. **CI-native, dependency-free core.** No cloud account, no Python/GPU runtime, no per-row/per-GB
   pricing — just a library on your classpath, with first-class JUnit 5
   (`@FillDatabase`/`@FillSource`) and Testcontainers integrations.
5. **Maintained and multi-DB.** PostgreSQL, MySQL, and CockroachDB, actively released — while
   several peers are dormant (synth) or feature-frozen (Benerator CE).

## Where it fits

Bloviate is an open-source, JDBC-native *filler* — it manufactures relational data from a schema.
That's a different job from the **ML/privacy synthesizers** (SDV, Gretel, MOSTLY AI), which learn
from real data and are non-deterministic, and from the **masking/subsetting** tools (Tonic,
Delphix, Jailer), which operate on existing production data. Among its actual peers — Benerator
(JVM, but FK is manually declared and the community edition is frozen), Snowfakery (Python,
recipe-authored, no schema introspection), synth (dormant since 2022), and DBeaver Mock Data (paid,
GUI-bound) — Bloviate is the maintained, deterministic, auto-FK option.

## Where the boundary is

Bloviate is *not* a statistical/ML synthesizer (it generates *specified* distributions, not ones
learned from real data) and *not* a masking/subsetting tool for existing data; it is JVM-only with
no GUI. Realistic semantic values (emails, names, addresses), correlated columns, and CHECK/enum
conformance are all supported — as opt-in features layered on the fast, type-driven default rather
than replacing it.

See [POSITIONING.md](https://github.com/timveil/bloviate/blob/main/POSITIONING.md) for the full
competitive landscape, with sources.
