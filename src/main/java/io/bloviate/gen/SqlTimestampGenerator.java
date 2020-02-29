package io.bloviate.gen;

import java.sql.Date;
import java.sql.Timestamp;

public class SqlTimestampGenerator implements DataGenerator<Timestamp> {

    private final Timestamp startInclusive;
    private final Timestamp endExclusive;

    @Override
    public Timestamp generate() {

        Long randomTime = new LongGenerator.Builder()
                .start(startInclusive.getTime())
                .end(endExclusive.getTime())
                .build().generate();

        return new Timestamp(randomTime);
    }

    @Override
    public String generateAsString() {
        return generate().toString();
    }

    public static class Builder {

        private Timestamp startInclusive = new Timestamp(0);
        private Timestamp endExclusive = new Timestamp(Long.MAX_VALUE);

        public Builder start(Timestamp start) {
            this.startInclusive = start;
            return this;
        }

        public Builder end(Timestamp end) {
            this.endExclusive = end;
            return this;
        }

        public SqlTimestampGenerator build() {
            return new SqlTimestampGenerator(this);
        }
    }

    private SqlTimestampGenerator(Builder builder) {
        this.startInclusive = builder.startInclusive;
        this.endExclusive = builder.endExclusive;
    }
}
