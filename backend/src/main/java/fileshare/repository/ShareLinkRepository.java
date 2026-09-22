package fileshare.repository;

import fileshare.entity.ShareLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {

    Optional<ShareLink> findByToken(String token);

    List<ShareLink> findTop20ByOrderByCreatedAtDesc();

    List<ShareLink> findTop20ByPathStartingWithOrderByCreatedAtDesc(String pathPrefix);

    long countByPathStartingWith(String pathPrefix);
}
