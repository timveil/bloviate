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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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

    final Logger logger = LoggerFactory.getLogger(getClass());


    private static final YAMLFactory yamlFactory = new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID);

    private static final ObjectMapper mapper = new ObjectMapper(yamlFactory)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

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

        logger.debug("writing to file [{}]", output);

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(output));
             CSVPrinter csvPrinter = new CSVPrinter(writer, format)) {

            printHeader(csvPrinter);

            for (int i = 0; i < rows; i++) {
                for (ColumnDefinition columnDefinition : columnDefinitions) {
                    csvPrinter.print(columnDefinition.dataGenerator().generateAsString());
                }
                csvPrinter.println();
            }

            csvPrinter.flush();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }

    }

    @Override
    public void yaml() {
        try {
            mapper.writeValue(new File(fileName + ".yaml"), this);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public String getFileName() {
        return fileName;
    }

    public FileDefinition getDefinition() {
        return definition;
    }

    public boolean isCompress() {
        return compress;
    }

    public List<ColumnDefinition> getColumnDefinitions() {
        return columnDefinitions;
    }

    public long getRows() {
        return rows;
    }

    private CSVFormat getCsvFormat(FileType fileType) {
        return switch (fileType) {
            case CSV -> CSVFormat.DEFAULT;
            case TDV -> CSVFormat.TDF;
            case PIPE -> CSVFormat.Builder.create().setDelimiter('|').build();
        };
    }

    private void printHeader(CSVPrinter csvPrinter) throws IOException {
        for (ColumnDefinition columnDefinition : columnDefinitions) {
            csvPrinter.print(columnDefinition.name());
        }
        csvPrinter.println();
    }

    public static class Builder {

        private final String fileName;

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

        public FlatFileGenerator build() {
            return new FlatFileGenerator(this);
        }
    }

    private FlatFileGenerator(Builder builder) {
        this.fileName = builder.fileName;
        this.columnDefinitions = builder.columnDefinitions;
        this.compress = builder.compress;
        this.definition = builder.definition;
        this.rows = builder.rows;
    }

}
