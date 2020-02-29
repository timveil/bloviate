package io.bloviate.gen;

import java.sql.Time;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class SqlTimeGenerator implements DataGenerator<Time> {

    private final Time startInclusive;
    private final Time endExclusive;

    @Override
    public Time generate() {

        Long randomTime = new LongGenerator.Builder()
                .start(startInclusive.getTime())
                .end(endExclusive.getTime())
                .build().generate();

        return new Time(randomTime);
    }

    @Override
    public String generateAsString() {
        return generate().toString();
    }

    public static class Builder {

        private Time startInclusive = new Time(Instant.EPOCH.toEpochMilli());
        private Time endExclusive = new Time(Instant.now().plus(100, ChronoUnit.HOURS).toEpochMilli());

        public Builder start(Time start) {
            this.startInclusive = start;
            return this;
        }

        public Builder end(Time end) {
            this.endExclusive = end;
            return this;
        }

        public SqlTimeGenerator build() {
            return new SqlTimeGenerator(this);
        }
    }

    private SqlTimeGenerator(Builder builder) {
        this.startInclusive = builder.startInclusive;
        this.endExclusive = builder.endExclusive;
    }
}
