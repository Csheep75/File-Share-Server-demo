package fileshare.repository;

import fileshare.entity.Clazz;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClazzRepository extends JpaRepository<Clazz, Long> {
    Optional<Clazz> findByInviteCode(String inviteCode);
}
