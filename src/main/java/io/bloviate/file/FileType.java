package io.bloviate.file;

public enum FileType {
    CSV(',', "csv"),
    TDV('\t', "txt"),
    PIPE('|', "txt");

    private final char delimiter;

    private final String extension;

    FileType(char delimiter, String extension) {
        this.delimiter = delimiter;
        this.extension = extension;
    }

    public char getDelimiter() {
        return delimiter;
    }

    public String getExtension() {
        return extension;
    }
}
