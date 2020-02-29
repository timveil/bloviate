package io.bloviate.file;

import io.bloviate.ColumnDefinition;
import io.bloviate.gen.IntegerGenerator;
import io.bloviate.gen.SimpleStringGenerator;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FlatFileTest {

    @Test
    void generate() {

        List<ColumnDefinition> definitions = new ArrayList<>();

        definitions.add(new ColumnDefinition("col1", new SimpleStringGenerator.Builder().length(10).build()));
        definitions.add(new ColumnDefinition("col2", new IntegerGenerator.Builder().start(1).end(100).build()));

        new FlatFile.Builder("test").addAll(definitions).build().generate();
    }
}