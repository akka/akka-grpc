package akka.grpc.javadsl;

public class BytesMetadataEntry implements MetadataEntry {
    private Byte[] value;

    public BytesMetadataEntry(Byte[] value) {
        this.value = value;
    }

    public Byte[] getValue() {
        return value;
    }
}
