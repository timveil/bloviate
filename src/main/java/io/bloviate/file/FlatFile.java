package io.bloviate.file;

import io.bloviate.ColumnDefinition;
import io.bloviate.CsvFile;
import io.bloviate.FileDefinition;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FlatFile implements FileGenerator {

    private final String fileName;
    private final FileDefinition definition;
    private final boolean compress;
    private final List<ColumnDefinition> columnDefinitions;
    private final long rows;

    @Override
    public File generate() {
        return null;
    }


    public static class Builder {

        private String fileName;
        private FileDefinition definition = new CsvFile();
        private boolean compress;
        private List<ColumnDefinition> columnDefinitions = new ArrayList<>();
        private long rows = 1000;

        public Builder name(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder output(FileDefinition fileDefinition) {
            this.definition = fileDefinition;
            return this;
        }

        public Builder compress() {
            this.compress = true;
            return this;
        }

        public Builder addAll(List<ColumnDefinition> columnDefinitions) {
            this.columnDefinitions = columnDefinitions;
            return this;
        }

        public Builder add(ColumnDefinition columnDefinition) {
            this.columnDefinitions.add(columnDefinition);
            return this;
        }

        public Builder rows(long rows) {
            this.rows = rows;
            return this;
        }

        public FlatFile build() {
            return new FlatFile(this);
        }
    }

    private FlatFile(Builder builder) {
        this.fileName = builder.fileName;
        this.columnDefinitions = builder.columnDefinitions;
        this.compress = builder.compress;
        this.definition  = builder.definition;
        this.rows = builder.rows;
    }

}
