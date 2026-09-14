package fileshare.repository;

import fileshare.entity.UserClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface UserClassRepository extends JpaRepository<UserClass, Long> {
    List<UserClass> findByUserId(Long userId);
    List<UserClass> findByClassId(Long classId);
    boolean existsByUserIdAndClassId(Long userId, Long classId);
    @Transactional
    void deleteByUserIdAndClassId(Long userId, Long classId);
    @Transactional
    void deleteByClassId(Long classId);
}
