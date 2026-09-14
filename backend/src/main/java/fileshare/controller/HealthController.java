package fileshare.controller;

import fileshare.service.DufsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final DufsService dufsService;

    public HealthController(DufsService dufsService) {
        this.dufsService = dufsService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "file-share-backend",
                "dufs", dufsService.ping() ? "UP" : "DOWN",
                "time", Instant.now().toString()
        );
    }
}
