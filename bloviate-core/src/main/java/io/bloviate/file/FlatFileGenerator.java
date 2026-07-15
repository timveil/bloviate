/*
 * Copyright (c) 2021 Tim Veil
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

package io.bloviate.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Generator for creating flat files (CSV, TSV, pipe-delimited) with synthetic data.
 * 
 * <p>The FlatFileGenerator creates delimited text files containing randomly generated
 * data based on configurable column definitions. It supports multiple output formats
 * including CSV, tab-delimited (TSV), and pipe-delimited files.
 * 
 * <p>Key features:
 * <ul>
 *   <li>Support for CSV, TSV, and pipe-delimited output formats</li>
 *   <li>Configurable number of rows and columns</li>
 *   <li>Automatic header generation with column names</li>
 *   <li>Integration with {@link ColumnDefinition} and {@link io.bloviate.gen.DataGenerator}</li>
 *   <li>YAML configuration export capability</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>{@code
 * List<ColumnDefinition> columns = Arrays.asList(
 *     new ColumnDefinition("id", new IntegerGenerator(random)),
 *     new ColumnDefinition("name", new StringGenerator(random))
 * );
 * 
 * new FlatFileGenerator.Builder("output-file")
 *     .addAll(columns)
 *     .rows(1000)
 *     .output(new CsvFile())
 *     .build()
 *     .generate();
 * }</pre>
 * 
 * @author Tim Veil
 * @see ColumnDefinition
 * @see FileDefinition
 * @see FileGenerator
 */
public class FlatFileGenerator implements FileGenerator {

    private static final Logger logger = LoggerFactory.getLogger(FlatFileGenerator.class);


    private static final YAMLFactory yamlFactory = new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID);

    private static final ObjectMapper mapper = new ObjectMapper(yamlFactory)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private final String fileName;
    private final FileDefinition definition;
    private final List<ColumnDefinition> columnDefinitions;
    private final long rows;

    @Override
    public void generate() {

        FileType fileType = definition.getFileType();
        String extension = fileType.getExtension();

        CSVFormat format = getCsvFormat(fileType);

        String output = fileName + '.' + extension;

        logger.debug("writing to file [{}]", output);

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(output));
             CSVPrinter csvPrinter = new CSVPrinter(writer, format)) {

            printHeader(csvPrinter);

            for (long i = 0; i < rows; i++) {
                for (ColumnDefinition columnDefinition : columnDefinitions) {
                    csvPrinter.print(columnDefinition.dataGenerator().generateAsString());
                }
                csvPrinter.println();
            }

            csvPrinter.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write file [" + output + "]", e);
        }

    }

    @Override
    public void yaml() {
        try {
            mapper.writeValue(new File(fileName + ".yaml"), this);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write YAML file [" + fileName + ".yaml]", e);
        }
    }

    /**
     * Returns the base output file name (the format-specific extension is appended at generation time).
     *
     * @return the base file name
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Returns the file format definition controlling the delimiter and extension.
     *
     * @return the file format definition
     */
    public FileDefinition getDefinition() {
        return definition;
    }

    /**
     * Returns the column definitions, in output order.
     *
     * @return an unmodifiable list of the column definitions, copied at build time
     */
    public List<ColumnDefinition> getColumnDefinitions() {
        return columnDefinitions;
    }

    /**
     * Returns the number of data rows to generate, excluding the header row.
     *
     * @return the data row count
     */
    public long getRows() {
        return rows;
    }

    private CSVFormat getCsvFormat(FileType fileType) {
        return switch (fileType) {
            case CSV -> CSVFormat.DEFAULT;
            case TDV -> CSVFormat.TDF;
            case PIPE -> CSVFormat.Builder.create().setDelimiter('|').get();
        };
    }

    private void printHeader(CSVPrinter csvPrinter) throws IOException {
        for (ColumnDefinition columnDefinition : columnDefinitions) {
            csvPrinter.print(columnDefinition.name());
        }
        csvPrinter.println();
    }

    /** Builds a {@link FlatFileGenerator}, defaulting to CSV output and {@code 1000} rows with no columns. */
    public static class Builder {

        private final String fileName;

        private FileDefinition definition = new CsvFile();
        private List<ColumnDefinition> columnDefinitions = new ArrayList<>();
        private long rows = 1000;

        /**
         * Creates a builder for the given base output file name (the extension is added per output format).
         *
         * @param fileName the base file name, without extension
         */
        public Builder(String fileName) {
            this.fileName = fileName;
        }


        /**
         * Sets the output file format definition. Defaults to {@link CsvFile} (CSV output)
         * when not specified.
         *
         * @param fileDefinition the file format definition controlling the delimiter and extension
         * @return this builder, for chaining
         */
        public Builder output(FileDefinition fileDefinition) {
            this.definition = fileDefinition;
            return this;
        }

        /**
         * Replaces the column definitions with the supplied list. The columns are written in the
         * order given, one delimited field per column.
         *
         * <p>The list is copied, so later changes to the caller's list are not observed and
         * {@link #add(ColumnDefinition)} keeps working even when an immutable list (e.g.
         * {@link List#of}) is supplied.
         *
         * @param columnDefinitions the column definitions to use
         * @return this builder, for chaining
         */
        public Builder addAll(List<ColumnDefinition> columnDefinitions) {
            this.columnDefinitions = new ArrayList<>(columnDefinitions);
            return this;
        }

        /**
         * Appends a single column definition to the existing list of columns.
         *
         * @param columnDefinition the column definition to append
         * @return this builder, for chaining
         */
        public Builder add(ColumnDefinition columnDefinition) {
            this.columnDefinitions.add(columnDefinition);
            return this;
        }

        /**
         * Sets the number of data rows to generate, excluding the header row. Defaults to
         * {@code 1000} when not specified.
         *
         * @param rows the number of data rows to write
         * @return this builder, for chaining
         */
        public Builder rows(long rows) {
            this.rows = rows;
            return this;
        }

        /**
         * Builds a new {@link FlatFileGenerator} from this builder's configuration.
         *
         * @return a configured {@link FlatFileGenerator}
         */
        public FlatFileGenerator build() {
            return new FlatFileGenerator(this);
        }
    }

    private FlatFileGenerator(Builder builder) {
        this.fileName = Objects.requireNonNull(builder.fileName, "fileName must not be null");
        // copy so reusing or mutating the builder after build() cannot affect this instance
        this.columnDefinitions = List.copyOf(builder.columnDefinitions);
        this.definition = Objects.requireNonNull(builder.definition, "definition must not be null");
        this.rows = builder.rows;
    }

}
