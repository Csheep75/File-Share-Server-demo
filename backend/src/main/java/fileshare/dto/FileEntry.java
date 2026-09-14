package fileshare.dto;

public record FileEntry(
        String name,
        String path,
        boolean directory,
        long size,
        String modifiedAt
) {
}
