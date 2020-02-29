package io.bloviate.gen;


import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class SqlDateGenerator implements DataGenerator<Date> {

    private final Date startInclusive;
    private final Date endExclusive;

    @Override
    public Date generate() {

        Long randomTime = new LongGenerator.Builder()
                .start(startInclusive.getTime())
                .end(endExclusive.getTime())
                .build().generate();

        return new Date(randomTime);
    }

    @Override
    public String generateAsString() {
        return generate().toString();
    }

    public static class Builder {

        private Date startInclusive = new Date(Instant.EPOCH.toEpochMilli());
        private Date endExclusive = new Date(Instant.now().plus(100, ChronoUnit.HOURS).toEpochMilli());

        public Builder start(Date start) {
            this.startInclusive = start;
            return this;
        }

        public Builder end(Date end) {
            this.endExclusive = end;
            return this;
        }

        public SqlDateGenerator build() {
            return new SqlDateGenerator(this);
        }
    }

    private SqlDateGenerator(Builder builder) {
        this.startInclusive = builder.startInclusive;
        this.endExclusive = builder.endExclusive;
    }
}
