package io.bloviate;

public abstract class FileDefinition {


    private final char delimiter;

    private final String extension;

    public FileDefinition(char delimiter, String extension) {
        this.delimiter = delimiter;
        this.extension = extension;
    }


}
