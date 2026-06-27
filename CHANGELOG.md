## [2.13.0](https://github.com/timveil/bloviate/compare/v2.12.0...v2.13.0) (2026-06-27)

## [2.12.0](https://github.com/timveil/bloviate/compare/v2.11.0...v2.12.0) (2026-06-27)

## [2.11.0](https://github.com/timveil/bloviate/compare/v2.10.0...v2.11.0) (2026-06-27)

## [2.10.0](https://github.com/timveil/bloviate/compare/v2.9.1...v2.10.0) (2026-06-27)

## [2.9.1](https://github.com/timveil/bloviate/compare/v2.9.0...v2.9.1) (2026-06-27)

## [2.9.0](https://github.com/timveil/bloviate/compare/v2.8.0...v2.9.0) (2026-06-27)

## [2.8.0](https://github.com/timveil/bloviate/compare/v2.7.0...v2.8.0) (2026-06-27)

## [2.7.0](https://github.com/timveil/bloviate/compare/v2.6.0...v2.7.0) (2026-06-27)

## [2.6.0](https://github.com/timveil/bloviate/compare/v2.5.0...v2.6.0) (2026-06-27)

## [2.5.0](https://github.com/timveil/bloviate/compare/v2.4.0...v2.5.0) (2026-06-27)

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
