package fileshare.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class PathUtils {

    private PathUtils() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank() || "/".equals(raw)) {
            return "/";
        }
        String cleaned = raw.replace('\\', '/');
        String[] parts = cleaned.split("/");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                throw new IllegalArgumentException("路径不合法");
            }
            builder.append('/').append(part);
        }
        return builder.isEmpty() ? "/" : builder.toString();
    }

    public static String parent(String path) {
        String normalized = normalize(path);
        int idx = normalized.lastIndexOf('/');
        if (idx <= 0) {
            return "/";
        }
        return normalized.substring(0, idx);
    }

    public static String fileName(String path) {
        String normalized = normalize(path);
        int idx = normalized.lastIndexOf('/');
        return idx < 0 ? normalized : normalized.substring(idx + 1);
    }

    public static String join(String directory, String name) {
        String dir = normalize(directory);
        String safeName = name == null ? "" : name.replace('\\', '/');
        if (safeName.contains("/") || safeName.contains("..")) {
            throw new IllegalArgumentException("文件名不合法");
        }
        if ("/".equals(dir)) {
            return "/" + safeName;
        }
        return dir + "/" + safeName;
    }

    public static String encodePath(String path) {
        String normalized = normalize(path);
        if ("/".equals(normalized)) {
            return "/";
        }
        return Arrays.stream(normalized.split("/"))
                .filter(part -> !part.isBlank())
                .map(part -> URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/", "/", ""));
    }

    public static boolean isDirectoryPath(String path) {
        return path != null && path.endsWith("/");
    }
}
