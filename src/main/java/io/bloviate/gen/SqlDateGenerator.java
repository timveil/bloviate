package io.bloviate.gen;


import java.sql.Date;

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

    public static class Builder {

        private Date startInclusive = new Date(Long.MIN_VALUE);
        private Date endExclusive = new Date(Long.MAX_VALUE);

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
