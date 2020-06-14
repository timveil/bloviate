/*
 * Copyright 2020 Tim Veil
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.bloviate.gen;

public class IntervalGenerator extends AbstractDataGenerator<String> {

    private final IntegerGenerator yearGenerator;
    private final IntegerGenerator monthGenerator;
    private final IntegerGenerator dayGenerator;
    private final IntegerGenerator hourGenerator;
    private final IntegerGenerator minuteGenerator;
    private final IntegerGenerator secondGenerator;

    @Override
    public String generate() {
        return String.format("%d-%d %d %d:%d:%d",
                yearGenerator.generate(),
                monthGenerator.generate(),
                dayGenerator.generate(),
                hourGenerator.generate(),
                minuteGenerator.generate(),
                secondGenerator.generate()
        );
    }

    @Override
    public String generateAsString() {
        return generate();
    }

    public static class Builder {
        public IntervalGenerator build() {
            return new IntervalGenerator(this);
        }
    }

    private IntervalGenerator(Builder builder) {
        this.yearGenerator = new IntegerGenerator.Builder().start(1).end(10).build();
        this.monthGenerator = new IntegerGenerator.Builder().start(1).end(12).build();
        this.dayGenerator = new IntegerGenerator.Builder().start(1).end(30).build();
        this.hourGenerator = new IntegerGenerator.Builder().start(1).end(24).build();
        this.minuteGenerator = new IntegerGenerator.Builder().start(1).end(60).build();
        this.secondGenerator = new IntegerGenerator.Builder().start(1).end(60).build();
    }
}
