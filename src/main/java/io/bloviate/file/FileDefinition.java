package io.bloviate.file;

public abstract class FileDefinition {

    private final FileType fileType;

    public FileDefinition(FileType fileType) {
        this.fileType = fileType;
    }

    public FileType getFileType() {
        return fileType;
    }

}
