package fileshare.controller;

import fileshare.dto.ApiResponse;
import fileshare.entity.ShareLink;
import fileshare.service.DufsService;
import fileshare.service.ShareService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/share")
public class ShareController {

    private final ShareService shareService;
    private final DufsService dufsService;

    public ShareController(ShareService shareService, DufsService dufsService) {
        this.shareService = shareService;
        this.dufsService = dufsService;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(
            @RequestParam String path,
            @RequestParam(required = false, defaultValue = "24") Integer expireHours
    ) {
        ShareLink link = shareService.create(path, expireHours);
        return ApiResponse.success(Map.of(
                "token", link.getToken(),
                "path", link.getPath(),
                "fileName", link.getFileName(),
                "expireAt", link.getExpireAt() == null ? "" : link.getExpireAt().toString(),
                "url", "/s/" + link.getToken()
        ));
    }

    @GetMapping
    public ApiResponse<List<ShareLink>> list() {
        return ApiResponse.success(shareService.recent());
    }

    @GetMapping("/{token}")
    public ResponseEntity<InputStreamResource> download(@PathVariable String token) {
        ShareLink link = shareService.consume(token);
        return dufsService.download(link.getPath(), false);
    }
}
