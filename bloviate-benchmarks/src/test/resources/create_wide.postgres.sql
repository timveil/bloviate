--
-- Copyright 2020 Tim Veil
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

-- A deliberately "wide" schema: many independent tables with no foreign keys between them.
-- They all sit in a single topological level, so they are the target for the parallel
-- table-fill optimization (#447, item #1). No primary keys: this is a raw-throughput fixture,
-- not an integrity fixture, and unconstrained columns avoid random-value collisions on a
-- generated key. Each table is filled with the configured default row count.

CREATE TABLE wide_t01 (c_int int, c_bigint bigint, c_name varchar(64), c_desc varchar(128), c_amount numeric(12, 2), c_score double precision, c_flag boolean, c_ts timestamp);
CREATE TABLE wide_t02 (c_int int, c_bigint bigint, c_name varchar(64), c_desc varchar(128), c_amount numeric(12, 2), c_score double precision, c_flag boolean, c_ts timestamp);
CREATE TABLE wide_t03 (c_int int, c_bigint bigint, c_name varchar(64), c_desc varchar(128), c_amount numeric(12, 2), c_score double precision, c_flag boolean, c_ts timestamp);
CREATE TABLE wide_t04 (c_int int, c_bigint bigint, c_name varchar(64), c_desc varchar(128), c_amount numeric(12, 2), c_score double precision, c_flag boolean, c_ts timestamp);
CREATE TABLE wide_t05 (c_int int, c_bigint bigint, c_name varchar(64), c_desc varchar(128), c_amount numeric(12, 2), c_score double precision, c_flag boolean, c_ts timestamp);
CREATE TABLE wide_t06 (c_int int, c_bigint bigint, c_name varchar(64), c_desc varchar(128), c_amount numeric(12, 2), c_score double precision, c_flag boolean, c_ts timestamp);
CREATE TABLE wide_t07 (c_int int, c_bigint bigint, c_name varchar(64), c_desc varchar(128), c_amount numeric(12, 2), c_score double precision, c_flag boolean, c_ts timestamp);
CREATE TABLE wide_t08 (c_int int, c_bigint bigint, c_name varchar(64), c_desc varchar(128), c_amount numeric(12, 2), c_score double precision, c_flag boolean, c_ts timestamp);
CREATE TABLE wide_t09 (c_int int, c_bigint bigint, c_name varchar(64), c_desc varchar(128), c_amount numeric(12, 2), c_score double precision, c_flag boolean, c_ts timestamp);
CREATE TABLE wide_t10 (c_int int, c_bigint bigint, c_name varchar(64), c_desc varchar(128), c_amount numeric(12, 2), c_score double precision, c_flag boolean, c_ts timestamp);
