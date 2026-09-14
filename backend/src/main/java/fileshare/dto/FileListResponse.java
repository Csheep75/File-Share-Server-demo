package fileshare.dto;

import java.util.List;

public record FileListResponse(String path, List<FileEntry> items) {
}
