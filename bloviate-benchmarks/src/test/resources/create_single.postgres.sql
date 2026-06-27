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

-- A single, wide table: the target for intra-table parallelism (#447 item #2, issue #466).
-- One table sits alone in its topological level, so between-table parallelism cannot help; only
-- partitioning its rows across workers does. No primary key (a raw-throughput fixture, not an
-- integrity one) so unconstrained columns avoid random-value collisions.

CREATE TABLE big_t (c_int int, c_bigint bigint, c_name varchar(64), c_desc varchar(128), c_amount numeric(12, 2), c_score double precision, c_flag boolean, c_ts timestamp);
