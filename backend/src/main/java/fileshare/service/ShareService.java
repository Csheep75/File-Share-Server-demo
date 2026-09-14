package fileshare.service;

import fileshare.entity.ShareLink;
import fileshare.repository.ShareLinkRepository;
import fileshare.util.PathUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class ShareService {

    private final ShareLinkRepository shareLinkRepository;

    public ShareService(ShareLinkRepository shareLinkRepository) {
        this.shareLinkRepository = shareLinkRepository;
    }

    public ShareLink create(String rawPath, Integer expireHours) {
        String path = PathUtils.normalize(rawPath);
        if ("/".equals(path)) {
            throw new IllegalArgumentException("不能分享根目录");
        }
        ShareLink link = new ShareLink();
        link.setToken(UUID.randomUUID().toString().replace("-", ""));
        link.setPath(path);
        link.setFileName(PathUtils.fileName(path));
        link.setCreatedAt(Instant.now());
        int hours = expireHours == null ? 24 : expireHours;
        if (hours > 0) {
            link.setExpireAt(Instant.now().plus(hours, ChronoUnit.HOURS));
        }
        link.setDownloadCount(0);
        return shareLinkRepository.save(link);
    }

    public List<ShareLink> recent() {
        return shareLinkRepository.findTop20ByOrderByCreatedAtDesc();
    }

    @Transactional
    public ShareLink consume(String token) {
        ShareLink link = shareLinkRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("分享链接不存在"));
        if (link.getExpireAt() != null && Instant.now().isAfter(link.getExpireAt())) {
            throw new IllegalArgumentException("分享链接已过期");
        }
        link.setDownloadCount(link.getDownloadCount() + 1);
        return shareLinkRepository.save(link);
    }
}
