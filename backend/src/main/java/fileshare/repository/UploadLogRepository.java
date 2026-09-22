package fileshare.repository;

import fileshare.entity.UploadLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UploadLogRepository extends JpaRepository<UploadLog, Long> {

    List<UploadLog> findTop12ByOrderByCreatedAtDesc();

    List<UploadLog> findTop12ByPathStartingWithOrderByCreatedAtDesc(String pathPrefix);

    long countByPathStartingWith(String pathPrefix);

    Optional<UploadLog> findTopByPathOrderByCreatedAtDesc(String path);

    List<UploadLog> findByPath(String path);

    List<UploadLog> findByPathStartingWith(String pathPrefix);

    @Transactional
    void deleteByPath(String path);

    @Transactional
    void deleteByPathStartingWith(String pathPrefix);
}
