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

-- A schema exercising issue #479 constraint conformance: an enum type plus CHECK constraints in the
-- common IN / BETWEEN / comparison forms across integer, numeric, double and varchar columns.

CREATE TYPE order_status AS ENUM ('NEW', 'PAID', 'SHIPPED', 'CANCELLED');

CREATE TABLE constrained (
    seq      integer,
    status   order_status NOT NULL,
    rating   integer CHECK (rating BETWEEN 1 AND 5),
    priority integer CHECK (priority IN (1, 2, 3)),
    grade    varchar(2) CHECK (grade IN ('A', 'B', 'C', 'D', 'F')),
    amount   numeric(8, 2) CHECK (amount >= 0 AND amount <= 9999.99),
    score    double precision CHECK (score >= 0 AND score <= 100)
);
