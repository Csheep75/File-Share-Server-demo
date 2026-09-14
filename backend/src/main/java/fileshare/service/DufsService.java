package fileshare.service;

import fileshare.config.DufsProperties;
import fileshare.dto.FileEntry;
import fileshare.dto.FileListResponse;
import fileshare.entity.UploadLog;
import fileshare.repository.UploadLogRepository;
import fileshare.util.PathUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DufsService {

    private final RestTemplate restTemplate;
    private final DufsProperties properties;
    private final UploadLogRepository uploadLogRepository;

    public DufsService(
            RestTemplate restTemplate,
            DufsProperties properties,
            UploadLogRepository uploadLogRepository
    ) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.uploadLogRepository = uploadLogRepository;
    }

    public FileListResponse list(String rawPath) {
        String path = PathUtils.normalize(rawPath);
        URI uri = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path(PathUtils.encodePath(path))
                .queryParam("json")
                .build(true)
                .toUri();
        Object body = restTemplate.getForObject(uri, Object.class);
        List<FileEntry> items = parseEntries(path, body);
        items.sort(Comparator
                .comparing(FileEntry::directory).reversed()
                .thenComparing(item -> item.name().toLowerCase(Locale.ROOT)));
        return new FileListResponse(path, items);
    }

    public FileListResponse search(String keyword) {
        return search(keyword, "/");
    }

    public FileListResponse search(String keyword, String basePath) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("请输入搜索关键词");
        }
        String searchPath = PathUtils.normalize(basePath);
        URI uri = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path(PathUtils.encodePath(searchPath))
                .queryParam("q", keyword.trim())
                .queryParam("json")
                .build()
                .encode()
                .toUri();
        Object body = restTemplate.getForObject(uri, Object.class);
        List<FileEntry> items = parseEntries(searchPath, body);
        return new FileListResponse(searchPath, items);
    }

    /**
     * 检查目标目录是否已存在同名同大小的文件
     */
    public FileEntry findDuplicate(String rawDir, String fileName, long fileSize) {
        String dir = PathUtils.normalize(rawDir);
        FileListResponse listing = list(dir);
        for (FileEntry entry : listing.items()) {
            if (!entry.directory()
                    && entry.name().equals(fileName)
                    && entry.size() == fileSize) {
                return entry;
            }
        }
        return null;
    }

    public void upload(String rawPath, MultipartFile file) throws IOException {
        upload(rawPath, file, false, null);
    }

    public FileEntry upload(String rawPath, MultipartFile file, boolean overwrite, Long userId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String name = PathUtils.fileName(original);
        String target = PathUtils.join(rawPath, name);

        // 去重检查：不覆盖时检测同名同大小文件
        if (!overwrite) {
            FileEntry dup = findDuplicate(rawPath, name, file.getSize());
            if (dup != null) {
                return dup;
            }
        }

        URI uri = dufsUri(target);
        RequestCallback callback = request -> {
            request.getHeaders().setContentType(MediaType.APPLICATION_OCTET_STREAM);
            request.getHeaders().setContentLength(file.getSize());
            try (InputStream in = file.getInputStream()) {
                in.transferTo(request.getBody());
            }
        };
        ResponseExtractor<Void> extractor = response -> null;
        restTemplate.execute(uri, HttpMethod.PUT, callback, extractor);

        UploadLog log = new UploadLog();
        log.setPath(target);
        log.setFileName(name);
        log.setSize(file.getSize());
        log.setCreatedAt(Instant.now());
        log.setUploadedBy(userId);
        uploadLogRepository.save(log);
        return null;
    }

    public void mkdir(String rawPath) {
        String path = PathUtils.normalize(rawPath);
        if ("/".equals(path)) {
            throw new IllegalArgumentException("目录名不能为空");
        }
        try {
            HttpClient client = HttpClient.newBuilder().build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(dufsUri(path))
                    .method("MKCOL", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("创建目录失败: " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("创建目录被中断", e);
        } catch (IOException e) {
            throw new RuntimeException("无法连接 dufs 文件服务器", e);
        }
    }

    public void delete(String rawPath) {
        String path = PathUtils.normalize(rawPath);
        if ("/".equals(path)) {
            throw new IllegalArgumentException("不能删除根目录");
        }
        restTemplate.exchange(dufsUri(path), HttpMethod.DELETE, null, Void.class);
    }

    public void rename(String fromRaw, String toName) {
        renameToPath(fromRaw, PathUtils.join(PathUtils.parent(PathUtils.normalize(fromRaw)), toName));
    }

    public void move(String fromRaw, String toDir) {
        String from = PathUtils.normalize(fromRaw);
        if ("/".equals(from)) {
            throw new IllegalArgumentException("不能移动根目录");
        }
        String name = PathUtils.fileName(from);
        String to = PathUtils.join(toDir, name);
        renameToPath(from, to);
    }

    private void renameToPath(String fromRaw, String toRaw) {
        String from = PathUtils.normalize(fromRaw);
        if ("/".equals(from)) {
            throw new IllegalArgumentException("不能重命名根目录");
        }
        String to = PathUtils.normalize(toRaw);
        try {
            HttpClient client = HttpClient.newBuilder().build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(dufsUri(from))
                    .method("MOVE", HttpRequest.BodyPublishers.noBody())
                    .header("Destination", properties.getBaseUrl() + PathUtils.encodePath(to))
                    .header("Overwrite", "F")
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("重命名失败: " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("重命名被中断", e);
        } catch (IOException e) {
            throw new RuntimeException("无法连接 dufs 文件服务器", e);
        }
    }

    public void copy(String fromRaw, String toDir) {
        String from = PathUtils.normalize(fromRaw);
        if ("/".equals(from)) {
            throw new IllegalArgumentException("不能复制根目录");
        }
        String name = PathUtils.fileName(from);
        String target = PathUtils.join(toDir, name);
        URI srcUri = dufsUri(from);
        URI dstUri = dufsUri(target);
        // 从 dufs 下载源文件，再 PUT 到目标路径
        byte[] data = restTemplate.execute(srcUri, HttpMethod.GET, null, response -> {
            InputStream in = response.getBody();
            return in != null ? in.readAllBytes() : new byte[0];
        });
        RequestCallback callback = request -> {
            request.getHeaders().setContentType(MediaType.APPLICATION_OCTET_STREAM);
            request.getHeaders().setContentLength(data.length);
            request.getBody().write(data);
        };
        restTemplate.execute(dstUri, HttpMethod.PUT, callback, response -> null);
    }

    public ResponseEntity<InputStreamResource> download(String rawPath, boolean inline) {
        String path = PathUtils.normalize(rawPath);
        String filename = PathUtils.fileName(path);
        URI uri = dufsUri(path);
        String guessed = URLConnection.guessContentTypeFromName(filename);
        MediaType mediaType = guessed != null ? MediaType.parseMediaType(guessed) : MediaType.APPLICATION_OCTET_STREAM;
        String disposition = (inline ? "inline" : "attachment")
                + "; filename=\"" + filename.replace("\"", "") + "\"";

        try {
            HttpClient client = HttpClient.newBuilder().build();

            // 先通过 HEAD 请求获取文件大小，以便设置 Content-Length
            HttpRequest headReq = HttpRequest.newBuilder().uri(uri).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<Void> headResp = client.send(headReq, HttpResponse.BodyHandlers.discarding());
            if (headResp.statusCode() >= 400) {
                throw new RuntimeException("dufs 返回错误状态: " + headResp.statusCode());
            }
            long contentLength = headResp.headers().firstValueAsLong("content-length").orElse(0L);

            // 用 GET 请求获取文件流
            HttpRequest getReq = HttpRequest.newBuilder().uri(uri).GET().build();
            HttpResponse<InputStream> getResp = client.send(getReq, HttpResponse.BodyHandlers.ofInputStream());
            InputStreamResource resource = new InputStreamResource(getResp.body());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .contentType(mediaType)
                    .contentLength(contentLength)
                    .body(resource);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("下载被中断", e);
        } catch (IOException e) {
            throw new RuntimeException("下载失败: " + e.getMessage(), e);
        }
    }

    public boolean ping() {
        try {
            restTemplate.getForEntity(
                    UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                            .path("/")
                            .queryParam("json")
                            .build(true)
                            .toUri(),
                    String.class
            );
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private URI dufsUri(String path) {
        return UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path(PathUtils.encodePath(path))
                .build(true)
                .toUri();
    }

    private List<FileEntry> parseEntries(String currentPath, Object body) {
        List<FileEntry> items = new ArrayList<>();
        if (body == null) {
            return items;
        }
        Object array = body;
        if (body instanceof Map<?, ?> map) {
            if (map.get("paths") != null) {
                array = map.get("paths");
            } else if (map.get("files") != null) {
                array = map.get("files");
            }
        }
        if (array instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> node) {
                    items.add(toEntry(currentPath, node));
                }
            }
        }
        return items;
    }

    private FileEntry toEntry(String currentPath, Map<?, ?> node) {
        String name = firstText(node, "name", "path", "href");
        if (name == null) {
            name = "unknown";
        }
        name = name.replaceAll("/+$", "");
        if (name.contains("/")) {
            name = PathUtils.fileName(name);
        }
        String kind = String.valueOf(value(node, "kind", ""));
        String pathType = String.valueOf(value(node, "path_type", ""));
        boolean directory = Boolean.parseBoolean(String.valueOf(value(node, "is_dir", false)))
                || "directory".equalsIgnoreCase(kind)
                || "dir".equalsIgnoreCase(kind)
                || "Directory".equalsIgnoreCase(pathType)
                || "Dir".equalsIgnoreCase(pathType);
        long size = 0;
        Object sizeValue = node.get("size");
        if (sizeValue instanceof Number number) {
            size = number.longValue();
        } else if (sizeValue != null) {
            try {
                size = Long.parseLong(sizeValue.toString());
            } catch (NumberFormatException ignored) {
                size = 0;
            }
        }
        String mtime = firstText(node, "mtime", "modified", "last_modified");
        String href = firstText(node, "path", "href");
        String path;
        if (href != null && href.startsWith("/")) {
            path = PathUtils.normalize(href);
        } else {
            path = PathUtils.join(currentPath, name);
        }
        return new FileEntry(name, path, directory, size, mtime == null ? "" : mtime);
    }

    private Object value(Map<?, ?> node, String key, Object fallback) {
        Object value = node.get(key);
        return value == null ? fallback : value;
    }

    private String firstText(Map<?, ?> node, String... fields) {
        for (String field : fields) {
            Object value = node.get(field);
            if (value != null && !String.valueOf(value).isBlank() && !"null".equals(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
    }
}
