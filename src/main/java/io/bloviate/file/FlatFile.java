package io.bloviate.file;

import io.bloviate.ColumnDefinition;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FlatFile implements FileGenerator {

    final Logger logger = LoggerFactory.getLogger(getClass());

    private final String fileName;
    private final FileDefinition definition;
    private final boolean compress;
    private final List<ColumnDefinition> columnDefinitions;
    private final long rows;

    @Override
    public void generate() {

        FileType fileType = definition.getFileType();
        String extension = fileType.getExtension();

        CSVFormat format = getCsvFormat(fileType);

        String output = fileName + '.' + extension;

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(output));
             CSVPrinter csvPrinter = new CSVPrinter(writer, format);
        ) {
            printHeader(csvPrinter);
            printHeader(csvPrinter);

            for (int i = 0; i < rows; i++) {
                for (ColumnDefinition columnDefinition : columnDefinitions) {
                    csvPrinter.print(columnDefinition.getDataGenerator().generateAsString());
                }
                csvPrinter.println();
            }

            csvPrinter.flush();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private CSVFormat getCsvFormat(FileType fileType) {
        CSVFormat format;
        switch (fileType) {
            case CSV:
                format = CSVFormat.DEFAULT;
                break;
            case TDV:
                format = CSVFormat.TDF;
                break;
            case PIPE:
                format = CSVFormat.DEFAULT.withDelimiter('|');
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + fileType);
        }
        return format;
    }

    private void printHeader(CSVPrinter csvPrinter) throws IOException {
        for (ColumnDefinition columnDefinition : columnDefinitions) {
            csvPrinter.print(columnDefinition.getHeader());
        }
        csvPrinter.println();
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
