package io.bloviate.file;

import io.bloviate.ColumnDefinition;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FlatFile implements FileGenerator {

    private final String fileName;
    private final FileDefinition definition;
    private final boolean compress;
    private final List<ColumnDefinition> columnDefinitions;
    private final long rows;

    @Override
    public void generate() {

        String extension = definition.getFileType().getExtension();
        String output = fileName + '.' + extension;

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(output));
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT);
        ) {

            for (int i = 0; i < rows; i++) {
                for (ColumnDefinition columnDefinition : columnDefinitions) {
                    csvPrinter.print(columnDefinition.getDataGenerator().generate());
                }
                csvPrinter.println();
            }

            csvPrinter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static class Builder {

        private String fileName;
        private FileDefinition definition = new CsvFile();
        private boolean compress;
        private List<ColumnDefinition> columnDefinitions = new ArrayList<>();
        private long rows = 1000;

        public Builder(String fileName) {
            this.fileName = fileName;
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
        this.definition = builder.definition;
        this.rows = builder.rows;
    }

}
