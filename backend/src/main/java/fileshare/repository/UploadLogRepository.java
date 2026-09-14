package fileshare.repository;

import fileshare.entity.UploadLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UploadLogRepository extends JpaRepository<UploadLog, Long> {

    List<UploadLog> findTop12ByOrderByCreatedAtDesc();

    java.util.Optional<UploadLog> findTopByPathOrderByCreatedAtDesc(String path);
}
