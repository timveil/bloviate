## [2.18.5](https://github.com/timveil/bloviate/compare/v2.18.4...v2.18.5) (2026-07-15)

### 🐛 Bug Fixes

* quote and schema-qualify insert identifiers, guard nullable column sizes ([#550](https://github.com/timveil/bloviate/issues/550)) ([aa59172](https://github.com/timveil/bloviate/commit/aa5917224448b59c4705daef6494b8eb6203a142))

## [2.18.4](https://github.com/timveil/bloviate/compare/v2.18.3...v2.18.4) (2026-06-30)

### 🐛 Bug Fixes

* **engine:** harden fill engine against memory/resource issues at scale ([#526](https://github.com/timveil/bloviate/issues/526)) ([#529](https://github.com/timveil/bloviate/issues/529)) ([dade406](https://github.com/timveil/bloviate/commit/dade4066fab714aca91a87657ea3565b8400bede))

## [2.18.3](https://github.com/timveil/bloviate/compare/v2.18.2...v2.18.3) (2026-06-30)

### 🔧 Build System

* stamp a stable Automatic-Module-Name into each jar ([#525](https://github.com/timveil/bloviate/issues/525)) ([9c56a49](https://github.com/timveil/bloviate/commit/9c56a49e976e4b3c06eefc00d1395b46b4f23834))

## [2.18.2](https://github.com/timveil/bloviate/compare/v2.18.1...v2.18.2) (2026-06-30)

### 🐛 Bug Fixes

* **gen:** render array generators as PostgreSQL array literals in generateAsString ([#528](https://github.com/timveil/bloviate/issues/528)) ([94df6bc](https://github.com/timveil/bloviate/commit/94df6bcc30a4c116c7689c0b68fa71c8d03c0c05))

## [2.18.1](https://github.com/timveil/bloviate/compare/v2.18.0...v2.18.1) (2026-06-30)

### ♻️ Code Refactoring

* simplify benchmark loop, adopt @NullMarked in bloviate-junit ([#524](https://github.com/timveil/bloviate/issues/524)) ([2b1fb5c](https://github.com/timveil/bloviate/commit/2b1fb5c65b81940002f0c52812c3fb4b6a4ff990))

## [2.18.0](https://github.com/timveil/bloviate/compare/v2.17.0...v2.18.0) (2026-06-29)

### ✨ Features

* **ext:** add MariaDB, H2, and SQLite database support ([#519](https://github.com/timveil/bloviate/issues/519)) ([0bc0a04](https://github.com/timveil/bloviate/commit/0bc0a041c79b0387f38fd0ef638f79569e1cc66a)), closes [#450](https://github.com/timveil/bloviate/issues/450) [#452](https://github.com/timveil/bloviate/issues/452) [#453](https://github.com/timveil/bloviate/issues/453) [#450](https://github.com/timveil/bloviate/issues/450) [#452](https://github.com/timveil/bloviate/issues/452) [#453](https://github.com/timveil/bloviate/issues/453)

## [2.17.0](https://github.com/timveil/bloviate/compare/v2.16.0...v2.17.0) (2026-06-29)

### ✨ Features

* **core:** add UNORDERED_BULK fill strategy (disable constraints, fill barrier-free, re-enable) ([#518](https://github.com/timveil/bloviate/issues/518)) ([3cb49f9](https://github.com/timveil/bloviate/commit/3cb49f976046162ec295974afdb3cf2235b5ca7f))

## [2.16.0](https://github.com/timveil/bloviate/compare/v2.15.7...v2.16.0) (2026-06-29)

### ✨ Features

* **website:** generate per-page OpenGraph images with Satori ([#517](https://github.com/timveil/bloviate/issues/517)) ([bfac882](https://github.com/timveil/bloviate/commit/bfac882b7807d070577cfcc50f2c54a3b77d6e5c))

## [2.15.7](https://github.com/timveil/bloviate/compare/v2.15.6...v2.15.7) (2026-06-29)

### 🐛 Bug Fixes

* **ci:** use --squash for dependabot auto-merge ([#512](https://github.com/timveil/bloviate/issues/512)) ([d03e929](https://github.com/timveil/bloviate/commit/d03e9291a5b08e38771acabe364661295fe10fa1))

## [2.15.6](https://github.com/timveil/bloviate/compare/v2.15.5...v2.15.6) (2026-06-29)

### 🐛 Bug Fixes

* **website:** repair Cloudflare deploy + add dependabot auto-merge ([#506](https://github.com/timveil/bloviate/issues/506)) ([6caf377](https://github.com/timveil/bloviate/commit/6caf377404a14558921d377a929a4ee55b2560c1))

## [2.15.5](https://github.com/timveil/bloviate/compare/v2.15.4...v2.15.5) (2026-06-28)

### 🔧 Build System

* **coverage:** enforce a per-package JaCoCo coverage floor ([#503](https://github.com/timveil/bloviate/issues/503)) ([ddfe71f](https://github.com/timveil/bloviate/commit/ddfe71fc9f3c9c375181acbce8fa78fe6a7e4273))

## [2.15.4](https://github.com/timveil/bloviate/compare/v2.15.3...v2.15.4) (2026-06-28)

### ♻️ Code Refactoring

* **logging:** consistent idiom, sane levels, and generator-resolution visibility ([#502](https://github.com/timveil/bloviate/issues/502)) ([065aaa7](https://github.com/timveil/bloviate/commit/065aaa7066c211c1545aeff3b226a380299656dc))

## [2.15.3](https://github.com/timveil/bloviate/compare/v2.15.2...v2.15.3) (2026-06-28)

### ♻️ Code Refactoring

* de-duplicate distribution generators and clear static-analysis findings ([#501](https://github.com/timveil/bloviate/issues/501)) ([e4deb98](https://github.com/timveil/bloviate/commit/e4deb98457e17a604d6e6d61d6c242ecead16101))

## [2.15.2](https://github.com/timveil/bloviate/compare/v2.15.1...v2.15.2) (2026-06-28)

### 🔧 Build System

* silence datafaker deprecation and shade uber-jar overlap warnings ([#497](https://github.com/timveil/bloviate/issues/497)) ([4e5cdd1](https://github.com/timveil/bloviate/commit/4e5cdd1cc0edc1070f52ccae54c63ef6e2ebac48))

## [2.15.1](https://github.com/timveil/bloviate/compare/v2.15.0...v2.15.1) (2026-06-28)

### 🔧 Build System

* give the core and parent artifacts meaningful descriptions ([#495](https://github.com/timveil/bloviate/issues/495)) ([4132c12](https://github.com/timveil/bloviate/commit/4132c12368926a65f16f61e97fd6ccd931372281))

## [2.15.0](https://github.com/timveil/bloviate/compare/v2.14.0...v2.15.0) (2026-06-28)

### ✨ Features

* **ext:** honor check and enum constraints on postgres ([#479](https://github.com/timveil/bloviate/issues/479) Part B) ([#491](https://github.com/timveil/bloviate/issues/491)) ([886340d](https://github.com/timveil/bloviate/commit/886340de304915144817332a4da262f3a33c934c)), closes [#472](https://github.com/timveil/bloviate/issues/472) [#473](https://github.com/timveil/bloviate/issues/473)

## [2.14.0](https://github.com/timveil/bloviate/compare/v2.13.0...v2.14.0) (2026-06-28)

### ✨ Features

* **datafaker:** referential realism — correlated person & geo columns ([#473](https://github.com/timveil/bloviate/issues/473)) ([#484](https://github.com/timveil/bloviate/issues/484)) ([4cfa56e](https://github.com/timveil/bloviate/commit/4cfa56eaf6a3adb52a61817ae2cffd9ea42752a8))

## [2.13.0](https://github.com/timveil/bloviate/compare/v2.12.0...v2.13.0) (2026-06-27)

### ✨ Features

* **datafaker:** optional semantic/realistic data via column-name inference ([#472](https://github.com/timveil/bloviate/issues/472)) ([#483](https://github.com/timveil/bloviate/issues/483)) ([3097f73](https://github.com/timveil/bloviate/commit/3097f734c26ff7d95c54a9398de985a5e12b1043))

## [2.12.0](https://github.com/timveil/bloviate/compare/v2.11.0...v2.12.0) (2026-06-27)

### ✨ Features

* **gen:** non-uniform value distributions ([#479](https://github.com/timveil/bloviate/issues/479) Part A) ([#482](https://github.com/timveil/bloviate/issues/482)) ([b26dfd3](https://github.com/timveil/bloviate/commit/b26dfd3fd88e8f430369fe6999d39089521749d9))

## [2.11.0](https://github.com/timveil/bloviate/compare/v2.10.0...v2.11.0) (2026-06-27)

### ✨ Features

* **perf:** intra-table partitioning, commit strategy, batch-rewrite warning ([#466](https://github.com/timveil/bloviate/issues/466), [#467](https://github.com/timveil/bloviate/issues/467), [#468](https://github.com/timveil/bloviate/issues/468)) ([5486aca](https://github.com/timveil/bloviate/commit/5486aca20aa2379471b5edd71e7377f782f44e96)), closes [#447](https://github.com/timveil/bloviate/issues/447)

## [2.10.0](https://github.com/timveil/bloviate/compare/v2.9.1...v2.10.0) (2026-06-27)

### ✨ Features

* **gen:** migrate RNG to java.util.random.RandomGenerator (L64X128MixRandom) ([cbff860](https://github.com/timveil/bloviate/commit/cbff860c54701eb48480e3605d31a4bb84fe3668)), closes [#471](https://github.com/timveil/bloviate/issues/471) [#471](https://github.com/timveil/bloviate/issues/471)

## [2.9.1](https://github.com/timveil/bloviate/compare/v2.9.0...v2.9.1) (2026-06-27)

### 🐛 Bug Fixes

* **gen:** make default temporal generators reproducible ([#464](https://github.com/timveil/bloviate/issues/464)) ([#465](https://github.com/timveil/bloviate/issues/465)) ([b97bb0f](https://github.com/timveil/bloviate/commit/b97bb0fac16c6bbf42a7f7611a85c441d9665b3d))

## [2.9.0](https://github.com/timveil/bloviate/compare/v2.8.0...v2.9.0) (2026-06-27)

### ✨ Features

* **perf:** parallel table fill, per-table commit, and hot-loop dispatch ([#447](https://github.com/timveil/bloviate/issues/447)) ([#463](https://github.com/timveil/bloviate/issues/463)) ([ac93f3f](https://github.com/timveil/bloviate/commit/ac93f3fbe4888e37c722d08531ee88e97dbe9eb0)), closes [#1](https://github.com/timveil/bloviate/issues/1) [#3](https://github.com/timveil/bloviate/issues/3) [#5](https://github.com/timveil/bloviate/issues/5) [#4](https://github.com/timveil/bloviate/issues/4)

## [2.8.0](https://github.com/timveil/bloviate/compare/v2.7.0...v2.8.0) (2026-06-27)

### ✨ Features

* **benchmarks:** add JMH and end-to-end fill benchmark harness ([b214b32](https://github.com/timveil/bloviate/commit/b214b323ed9abad7558d98a45434387685463f0f)), closes [#447](https://github.com/timveil/bloviate/issues/447)

## [2.7.0](https://github.com/timveil/bloviate/compare/v2.6.0...v2.7.0) (2026-06-27)

### ✨ Features

* add JUnit 5 and Testcontainers integration modules ([73f4c63](https://github.com/timveil/bloviate/commit/73f4c6332891154ff4bddcbc8a002ca0b3d71b37)), closes [#448](https://github.com/timveil/bloviate/issues/448)

## [2.6.0](https://github.com/timveil/bloviate/compare/v2.5.0...v2.6.0) (2026-06-27)

### ✨ Features

* **ext:** pluggable custom data generators via registry + ServiceLoader ([6bd1224](https://github.com/timveil/bloviate/commit/6bd1224edc078b813f1fc05c01fafa341d0a4a45)), closes [#446](https://github.com/timveil/bloviate/issues/446)

## [2.5.0](https://github.com/timveil/bloviate/compare/v2.4.0...v2.5.0) (2026-06-27)

### ✨ Features

* **ext:** full PostgreSQL and MySQL vendor-type support ([f17e03f](https://github.com/timveil/bloviate/commit/f17e03fd49de2863538692e63dcf1c34b163938d)), closes [#443](https://github.com/timveil/bloviate/issues/443)

## [2.4.0](https://github.com/timveil/bloviate/compare/v2.3.0...v2.4.0) (2026-06-26)

### ✨ Features

* **tpcc:** model delivery state — o_carrier_id and ol_delivery_d NULL for undelivered orders ([e884998](https://github.com/timveil/bloviate/commit/e8849989d4f407c2249bdb2cb281798c846b8c33)), closes [#421](https://github.com/timveil/bloviate/issues/421)

## [2.3.0](https://github.com/timveil/bloviate/compare/v2.2.0...v2.3.0) (2026-06-26)

### ✨ Features

* **tpcc:** c_last load enumeration (gap 4, part B) — completes gap 4 ([e4460f0](https://github.com/timveil/bloviate/commit/e4460f04ed97603d51942080083e94f0b8eeca37))
* **tpcc:** per-district o_c_id permutation via generic GroupedPermutationGenerator (gap 4, part A) ([db1f9a5](https://github.com/timveil/bloviate/commit/db1f9a5cc16e94ef413009151dd8518268b2805f))

## [2.2.0](https://github.com/timveil/bloviate/compare/v2.1.0...v2.2.0) (2026-06-26)

### ✨ Features

* **tpcc:** variable order-line count per order (gap 2), as reusable generators ([8545148](https://github.com/timveil/bloviate/commit/85451484ed2ea3447f10a7c6d9d218aa16eea4c3))

## [2.1.0](https://github.com/timveil/bloviate/compare/v2.0.0...v2.1.0) (2026-06-26)

### ✨ Features

* **tpcc:** close data-generation fidelity gaps 1 and 3 vs the spec ([8ab35d2](https://github.com/timveil/bloviate/commit/8ab35d21106cb05f8b698b78e7aa944211d633a9))

## [2.0.0](https://github.com/timveil/bloviate/compare/v1.0.3...v2.0.0) (2026-06-26)

### ⚠ BREAKING CHANGES

* DatabaseSupport no longer declares the build*Generator
methods; external subclasses that overrode them must register factories
via configure(Map) instead.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>

### ✨ Features

* add general-purpose data generators ([fb56565](https://github.com/timveil/bloviate/commit/fb56565a4ed1457de6708f293125837337795e96))
* add product-name-based DatabaseSupport selection ([8068197](https://github.com/timveil/bloviate/commit/8068197eec90645a64d41350f864faf0c7e07efd))
* add TPC-C data generation support ([2597bd3](https://github.com/timveil/bloviate/commit/2597bd3e22ad92ec76e8821f3b6f7b7e11c3b510))
* implement JsonbGenerator.generate and document SqlStructGenerator stub ([ae76e75](https://github.com/timveil/bloviate/commit/ae76e75b6743906c7f8f874697f7d6683842791a))
* support per-column generator overrides (column configuration) ([36fa11a](https://github.com/timveil/bloviate/commit/36fa11a0d01f858ebf44a27e30221828a7504243))

### 🐛 Bug Fixes

* correct numeric generation and null-unboxing bugs ([a35d84e](https://github.com/timveil/bloviate/commit/a35d84e34876b6a43bf00228d214bbdae68497c4))
* fail fast on an invalid SequentialIntegerGenerator range ([30ef810](https://github.com/timveil/bloviate/commit/30ef8109c75224c54ca1a5f9e505cfbe12b9df5a)), closes [#419](https://github.com/timveil/bloviate/issues/419)
* generate valid bit strings for CockroachDB bit/varbit columns ([26ee279](https://github.com/timveil/bloviate/commit/26ee279970efd406d23900159aaf5f59a68e42b7))
* honor numbers() in SimpleStringGenerator builder ([6e67a30](https://github.com/timveil/bloviate/commit/6e67a30a0f247a032ea6ba5eee4a2963f0ac76b6))
* implement get(ResultSet, int) for generators that returned null ([b388b8c](https://github.com/timveil/bloviate/commit/b388b8c890557d605a599f9490b583ca173a76da)), closes [#422](https://github.com/timveil/bloviate/issues/422)
* return null from primitive getters for SQL NULL columns ([eed69a5](https://github.com/timveil/bloviate/commit/eed69a5054ffabc0fd550140fdc6f3fb80e47af6))

### ♻️ Code Refactoring

* cache SeededRandomUtils in AbstractDataGenerator ([01b8d32](https://github.com/timveil/bloviate/commit/01b8d32aa16f3db44f2adf0712561b53723c5adb))
* improve readability and streamline APIs across modules ([8559938](https://github.com/timveil/bloviate/commit/8559938df8562469fa8108419c47615c8af96bdb))
* make the generator Builder contract generic and type-safe ([8e83056](https://github.com/timveil/bloviate/commit/8e8305643c2d56af078d8d775ba2c86d02df702c))
* replace DatabaseSupport type switch with a JDBCType registry ([20a308a](https://github.com/timveil/bloviate/commit/20a308a9b2a87a813acc94073033ff23ffc73f17))

### 🔧 Build System

* upgrade to Java 25 LTS and modernize the test stack ([76baecc](https://github.com/timveil/bloviate/commit/76baeccacd278b852f31b9486954cae5dc6f8fa2))

## [1.0.3](https://github.com/timveil/bloviate/compare/v1.0.2...v1.0.3) (2025-08-23)

### 🐛 Bug Fixes

* correct Maven settings.xml environment variable resolution ([5305972](https://github.com/timveil/bloviate/commit/5305972a66712fa1758f098dd92bd62c10f1a113))

## [1.0.2](https://github.com/timveil/bloviate/compare/v1.0.1...v1.0.2) (2025-08-23)

### 🐛 Bug Fixes

* correct Maven server-id configuration for GitHub Packages authentication ([324145f](https://github.com/timveil/bloviate/commit/324145f6344095b40539b0a2874c1b5d0ad48410))

## [1.0.1](https://github.com/timveil/bloviate/compare/v1.0.0...v1.0.1) (2025-08-23)

### 🐛 Bug Fixes

* resolve Maven authentication for GitHub Packages deployment ([01fef8e](https://github.com/timveil/bloviate/commit/01fef8ed82139781796cae11be0102a1ffc0bb69))

## 1.0.0 (2025-08-23)

### ✨ Features

* add comprehensive Javadoc documentation ([a9b3874](https://github.com/timveil/bloviate/commit/a9b3874d2894c2a76aa90a2ba4b07d30b56e9e4b))
