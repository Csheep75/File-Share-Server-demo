package fileshare.controller;

import fileshare.dto.ApiResponse;
import fileshare.dto.FileEntry;
import fileshare.dto.FileListResponse;
import fileshare.entity.User;
import fileshare.entity.UploadLog;
import fileshare.repository.ShareLinkRepository;
import fileshare.repository.UploadLogRepository;
import fileshare.repository.UserClassRepository;
import fileshare.service.DufsService;
import fileshare.util.PathUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final DufsService dufsService;
    private final UploadLogRepository uploadLogRepository;
    private final ShareLinkRepository shareLinkRepository;
    private final UserClassRepository userClassRepository;

    public FileController(
            DufsService dufsService,
            UploadLogRepository uploadLogRepository,
            ShareLinkRepository shareLinkRepository,
            UserClassRepository userClassRepository
    ) {
        this.dufsService = dufsService;
        this.uploadLogRepository = uploadLogRepository;
        this.shareLinkRepository = shareLinkRepository;
        this.userClassRepository = userClassRepository;
    }

    /**
     * 将 classId + 用户路径拼接为 dufs 实际路径: /{classId}/userPath
     */
    private String classPath(Long classId, String userPath) {
        String normalized = PathUtils.normalize(userPath);
        if ("/".equals(normalized)) {
            return "/" + classId;
        }
        return "/" + classId + normalized;
    }

    /**
     * 将 dufs 返回的路径去掉 classId 前缀，转为用户相对路径
     */
    private FileListResponse toUserResponse(Long classId, FileListResponse response) {
        String prefix = "/" + classId;
        String userPath = response.path().equals(prefix) ? "/" : response.path().substring(prefix.length());
        List<FileEntry> userItems = response.items().stream().map(item -> {
            String itemPath = item.path().startsWith(prefix) ? item.path().substring(prefix.length()) : item.path();
            if (itemPath.isEmpty()) itemPath = "/";
            return new FileEntry(item.name(), itemPath, item.directory(), item.size(), item.modifiedAt());
        }).toList();
        return new FileListResponse(userPath, userItems);
    }

    private void checkClassAccess(Long classId, User user) {
        if (user.getRole() != User.Role.ADMIN && !userClassRepository.existsByUserIdAndClassId(user.getId(), classId)) {
            throw new IllegalArgumentException("无权访问该班级文件");
        }
        // 确保班级目录在 dufs 中存在（自动修复历史数据）
        ensureClassDir(classId);
    }

    /**
     * 检查用户是否有权删除指定文件：管理员可以删除任何文件，普通用户只能删除自己上传的文件
     */
    private void checkDeletePermission(String dufsPath, User user) {
        if (user.getRole() == User.Role.ADMIN) {
            return;
        }
        var log = uploadLogRepository.findTopByPathOrderByCreatedAtDesc(dufsPath);
        if (log.isEmpty() || !user.getId().equals(log.get().getUploadedBy())) {
            throw new IllegalArgumentException("只能删除自己上传的文件");
        }
    }

    private void ensureClassDir(Long classId) {
        try {
            String dirPath = "/" + classId;
            // 检查目录是否存在
            var listing = dufsService.list(dirPath);
            // list 成功说明目录已存在
        } catch (Exception e) {
            // 目录不存在，尝试创建
            try {
                dufsService.mkdir("/" + classId);
            } catch (Exception ignored) {
                // 可能已存在，忽略
            }
        }
    }

    @GetMapping("/list")
    public ApiResponse<FileListResponse> list(@RequestParam Long classId,
                                               @RequestParam(defaultValue = "/") String path,
                                               @AuthenticationPrincipal User user) {
        checkClassAccess(classId, user);
        return ApiResponse.success(toUserResponse(classId, dufsService.list(classPath(classId, path))));
    }

    @GetMapping("/search")
    public ApiResponse<FileListResponse> search(@RequestParam Long classId,
                                                 @RequestParam String q,
                                                 @AuthenticationPrincipal User user) {
        checkClassAccess(classId, user);
        return ApiResponse.success(toUserResponse(classId, dufsService.search(q, "/" + classId)));
    }

    @PostMapping("/upload")
    public ApiResponse<FileEntry> upload(
            @RequestParam Long classId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "/") String path,
            @RequestParam(defaultValue = "false") boolean overwrite,
            @AuthenticationPrincipal User user
    ) throws Exception {
        checkClassAccess(classId, user);
        String dufsPath = classPath(classId, path);
        FileEntry duplicate = dufsService.upload(dufsPath, file, overwrite, user.getId());
        if (duplicate != null) {
            return new ApiResponse<>(false, "duplicate", duplicate);
        }
        return ApiResponse.success("上传成功", null);
    }

    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(@RequestParam Long classId,
                                                           @RequestParam String path,
                                                           @AuthenticationPrincipal User user) {
        checkClassAccess(classId, user);
        return dufsService.download(classPath(classId, path), false);
    }

    @GetMapping("/preview")
    public ResponseEntity<InputStreamResource> preview(@RequestParam Long classId,
                                                          @RequestParam String path,
                                                          @AuthenticationPrincipal User user) {
        checkClassAccess(classId, user);
        return dufsService.download(classPath(classId, path), true);
    }

    @DeleteMapping("/delete")
    public ApiResponse<Void> delete(@RequestParam Long classId,
                                     @RequestParam String path,
                                     @AuthenticationPrincipal User user) {
        checkClassAccess(classId, user);
        String dufsPath = classPath(classId, path);
        checkDeletePermission(dufsPath, user);
        dufsService.delete(dufsPath);
        return ApiResponse.success("已删除", null);
    }

    @PostMapping("/mkdir")
    public ApiResponse<Void> mkdir(@RequestParam Long classId,
                                    @RequestParam String path,
                                    @AuthenticationPrincipal User user) {
        checkClassAccess(classId, user);
        dufsService.mkdir(classPath(classId, path));
        return ApiResponse.success("目录已创建", null);
    }

    @PostMapping("/rename")
    public ApiResponse<Void> rename(@RequestParam Long classId,
                                     @RequestParam String path,
                                     @RequestParam String name,
                                     @AuthenticationPrincipal User user) {
        checkClassAccess(classId, user);
        dufsService.rename(classPath(classId, path), name);
        return ApiResponse.success("已重命名", null);
    }

    @PostMapping("/move")
    public ApiResponse<Void> move(@RequestParam Long classId,
                                   @RequestParam String from,
                                   @RequestParam String to,
                                   @AuthenticationPrincipal User user) {
        checkClassAccess(classId, user);
        // from 和 to 都是用户相对路径，需要都加上 classId 前缀
        dufsService.move(classPath(classId, from), classPath(classId, to));
        return ApiResponse.success("已移动", null);
    }

    @PostMapping("/copy")
    public ApiResponse<Void> copy(@RequestParam Long classId,
                                   @RequestParam String from,
                                   @RequestParam String to,
                                   @AuthenticationPrincipal User user) {
        checkClassAccess(classId, user);
        dufsService.copy(classPath(classId, from), classPath(classId, to));
        return ApiResponse.success("已复制", null);
    }

    @PostMapping("/batch-delete")
    public ApiResponse<Map<String, Object>> batchDelete(@RequestParam Long classId,
                                                         @RequestParam String paths,
                                                         @AuthenticationPrincipal User user) {
        checkClassAccess(classId, user);
        String[] list = paths.split(",");
        int success = 0;
        int failed = 0;
        for (String p : list) {
            String trimmed = p.trim();
            if (trimmed.isEmpty()) continue;
            try {
                String dufsPath = classPath(classId, trimmed);
                checkDeletePermission(dufsPath, user);
                dufsService.delete(dufsPath);
                success++;
            } catch (Exception e) {
                failed++;
            }
        }
        return ApiResponse.success(Map.of("success", success, "failed", failed));
    }

    @GetMapping("/recent")
    public ApiResponse<List<UploadLog>> recent(@RequestParam Long classId,
                                                @AuthenticationPrincipal User user) {
        checkClassAccess(classId, user);
        return ApiResponse.success(uploadLogRepository.findTop12ByOrderByCreatedAtDesc());
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(@RequestParam Long classId,
                                                   @AuthenticationPrincipal User user) {
        checkClassAccess(classId, user);
        long files = 0;
        long folders = 0;
        long bytes = 0;
        boolean dufsUp = dufsService.ping();
        if (dufsUp) {
            FileListResponse root = dufsService.list("/" + classId);
            files = root.items().stream().filter(item -> !item.directory()).count();
            folders = root.items().stream().filter(FileEntry::directory).count();
            bytes = root.items().stream().mapToLong(FileEntry::size).sum();
        }
        return ApiResponse.success(Map.of(
                "rootFiles", files,
                "rootFolders", folders,
                "rootBytes", bytes,
                "uploads", uploadLogRepository.count(),
                "shares", shareLinkRepository.count(),
                "dufs", dufsUp
        ));
    }
}
