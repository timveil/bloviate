# Why Bloviate

Where Bloviate sits in the test-data-generation landscape, who its real peers are, and when it's the
right (or wrong) tool for the job. If you just want to *use* it, start with the
[quickstart](/guides/quickstart/); this page is for evaluators deciding **whether Bloviate fits their
problem**.

> **A note on freshness.** The competitive facts below (licenses, maintenance status, ownership) were
> verified mid-2026 and are cited. This space moves fast — treat the citations as the source of truth
> and tell us if something has drifted.

## The one axis that matters

Test-data tools split on a single question: **do you generate from scratch, or derive from
production?**

- **Generate-from-scratch** — manufacture data from a schema/spec; production data is never touched.
- **Derive-from-production** — copy real data, then mask, subset, or learn-and-resynthesize it.

**Bloviate is firmly generate-from-scratch.** It reads your *schema* (not your data) and fills it.
That single choice is why the privacy story is simple and why it shares almost nothing with the
ML/masking platforms it's sometimes lumped in with.

## The market map

| Category | What it does | Representative tools | Same problem as Bloviate? |
|---|---|---|---|
| Value libraries | Emit realistic *values*; you wire up schema, DB, and FKs yourself | Datafaker, faker.js, Faker (Py) | Partial — a building block, not a filler |
| Declarative / recipe | Describe rows in YAML/JSON/GUI; it manufactures them | Snowfakery, Mockaroo, Benerator | **Yes** |
| **DB-native fillers** | Read a live schema and `INSERT` generated rows directly | **Bloviate**, DBeaver Mock Data, Redgate, dbForge | **Yes — home category** |
| ML / AI synthetic | Learn real distributions and emit faithful copies | SDV, Gretel, MOSTLY AI, YData | No — needs real data, non-deterministic |
| Subsetting / masking | Mask + subset *existing* production data | Tonic, Delphix, Neosync, Jailer | No — operates on production data |
| Benchmark generators | Fixed-schema data for TPC-style load tests | pgbench, HammerDB, BenchBase | No — fixed schemas only |

Bloviate's home category — **open-source, DB-native fillers that generate from the schema alone** — is
unusually thin. The strong commercial entries are GUI/Windows/SQL-Server-leaning and proprietary, and
the open-source schema-aware tools mostly *subset or mask existing data* rather than generate from
types.

## Closest functional rivals

These are the tools that actually overlap Bloviate's job — generate relational test data from a
schema — and how each differs (verified mid-2026):

| Tool | Stack & license | Overlap | Where it diverges from Bloviate |
|---|---|---|---|
| **Benerator CE** | Java/JDBC, dual GPL-2.0-with-exceptions / commercial | Model-driven relational test-data generation on the JVM | Relationships are **declared in the descriptor** (e.g. `<reference>` selectors) — no automatic FK-graph + topological fill order. **CE is no longer actively maintained** — kept "for legacy usage" only; the vendor steers new work to the separate DATAMIMIC product. |
| **Snowfakery** | Python, BSD-3-Clause | Generates related rows, deterministic-ish | Relations are **authored as YAML recipes** — it does **not** introspect a database schema; Salesforce-origin. Actively maintained (v4.2.1, Jan 2026). |
| **synth** | Rust CLI, Apache-2.0 | *Can* introspect a DB and generate to Postgres/MySQL/Mongo with `--seed` | **Effectively dormant** — last release v0.6.9, **Nov 2022**; no CockroachDB. |
| **DBeaver Mock Data** | Java/JDBC (GUI) | Schema-aware insert with an FK-aware generator and a seed | **Enterprise/Ultimate edition only** (not Community) and **GUI-bound**; oriented to filling tables whose parents already exist rather than ordering a whole schema from scratch. |

The ML/privacy platforms (SDV, Gretel, MOSTLY AI, Tonic) solve a *different* problem: they need real
seed data and are non-deterministic. They are not direct competitors — and several have notable
license/ownership constraints (SDV is **BSL/BUSL-1.1**, source-available rather than OSI open-source;
Gretel was **acquired by NVIDIA in March 2025**; MOSTLY AI now operates as "MOSTLY AI powered by
Syntho").

## Strengths

1. **An intersection few tools occupy.** Open-source **and** JVM/JDBC-native **and** automatic schema
   introspection **and** automatic FK **topological** fill ordering **and** RNG-deterministic **and**
   JUnit/Testcontainers-native **and** needs no production data. Each rival above is missing at least
   one of these.
2. **Zero-config foreign-key correctness.** Point it at a schema and it derives the fill order from
   `DatabaseMetaData` (see [the dependency DAG](./ARCHITECTURE.md#the-dependency-dag--fill-order-via-topological-sort)).
   Benerator and Snowfakery make you hand-author relationships; DBeaver expects parents to pre-exist.
3. **In-process, dependency-free, CI-native.** No cloud account, no Python/GPU runtime, no
   per-row/per-GB pricing — just a library on your classpath, with first-class
   [`@FillDatabase`/`@FillSource`](/guides/integrations/) and Testcontainers support.
4. **Privacy-safe by construction.** Generates from schema/type and never reads production, so the
   GDPR/CCPA exposure surface is structurally minimal — no masking or differential-privacy machinery
   to configure or trust.
5. **Determinism as a first-class contract.** Same seed + same schema ⇒ byte-identical data, even
   under parallel and partitioned fills (see [reproducibility](./ARCHITECTURE.md#reproducibility--deterministic-seeds-from-schema-identity)).
   Most ML synthesizers cannot offer byte-identical reruns.
6. **Maintained and multi-DB** — PostgreSQL, MySQL, and CockroachDB, actively released — while synth
   is dormant and Benerator CE is frozen.

## Limitations

Stated plainly, so the boundary is clear:

1. **No learned statistical fidelity.** It generates *specified* distributions, not ones learned from
   real data — SDV / Gretel / MOSTLY AI are the right tools for ML-training or analytics realism.
2. **No PII masking / de-identification** of existing data — that's Tonic / Delphix / Neosync / Jailer.
3. **JVM-only reach** — invisible to the Python-centric data-science world.
4. **No GUI / low-code surface** — it's a library and a CI dependency.

## When to use Bloviate

In one line:

> **The maintained, open-source, JDBC-native, FK-aware, deterministic test-data filler for JVM
> developers' CI pipelines.**

**Reach for Bloviate when** you need to populate a real relational schema with valid, reproducible
test data — for CI, integration tests, demos, or local development — without copying production and
without hand-writing the relationships. Its automatic FK ordering, deterministic seeding, and
JUnit/Testcontainers integration are built for exactly that.

**Look elsewhere when** your problem is a different one:

- you need data that statistically matches real distributions/correlations (ML training, analytics) — an ML synthesizer (SDV, Gretel, MOSTLY AI);
- you need to de-identify or subset *existing* production data — a masking/subsetting tool (Tonic, Delphix, Neosync, Jailer);
- you only need a stream of fake *values* and will wire up the schema and inserts yourself — a value library (Datafaker, faker.js).

Among the tools that *do* overlap Bloviate's job, each differs on an axis that may or may not matter
for your use case: Benerator (FK relationships are declared by hand; its community edition is frozen),
synth (dormant), Snowfakery (Python, recipe-authored, no schema introspection), and DBeaver Mock Data
(paid edition, GUI-only).

## Sources

Verified 2026-06 via web survey + codebase analysis.

- Benerator CE — <https://github.com/rapiddweller/rapiddweller-benerator-ce> · <https://benerator.de/faq/>
- Snowfakery — <https://github.com/SFDO-Tooling/Snowfakery>
- synth (releases) — <https://github.com/shuttle-hq/synth/releases>
- DBeaver Mock Data — <https://dbeaver.com/docs/dbeaver/Mock-Data-Generation/>
- SDV license (BSL) — <https://datacebo.com/blog/sdv-bsl-license/> · <https://github.com/sdv-dev/SDV/blob/main/LICENSE>
- Gretel → NVIDIA — <https://techcrunch.com/2025/03/19/nvidia-reportedly-acquires-synthetic-data-startup-gretel/>
- MOSTLY AI (powered by Syntho) — <https://mostly.ai/>
- Datafaker — <https://github.com/datafaker-net/datafaker>
- Jailer — <https://github.com/Wisser/Jailer>
- pgbench — <https://www.postgresql.org/docs/current/pgbench.html>
