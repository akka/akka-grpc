package akka.grpc.javadsl;

public class StringMetadataEntry implements MetadataEntry {
    private String value;

    public StringMetadataEntry(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
