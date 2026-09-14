package fileshare.config;

import fileshare.entity.Clazz;
import fileshare.entity.User;
import fileshare.repository.ClazzRepository;
import fileshare.repository.UserRepository;
import fileshare.service.DufsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final ClazzRepository clazzRepository;
    private final PasswordEncoder passwordEncoder;
    private final DufsService dufsService;

    public DataInitializer(UserRepository userRepository, ClazzRepository clazzRepository,
                           PasswordEncoder passwordEncoder, DufsService dufsService) {
        this.userRepository = userRepository;
        this.clazzRepository = clazzRepository;
        this.passwordEncoder = passwordEncoder;
        this.dufsService = dufsService;
    }

    @Override
    public void run(String... args) {
        // 创建默认管理员
        if (!userRepository.existsByUsername("root")) {
            User admin = new User();
            admin.setUsername("root");
            admin.setPassword(passwordEncoder.encode("seunet"));
            admin.setRole(User.Role.ADMIN);
            admin.setCreatedAt(Instant.now());
            userRepository.save(admin);
            log.info("已创建默认管理员账号: root");
        }

        // 确保所有班级在 dufs 中都有对应目录
        try {
            List<Clazz> classes = clazzRepository.findAll();
            for (Clazz clazz : classes) {
                try {
                    dufsService.mkdir("/" + clazz.getId());
                } catch (Exception e) {
                    // 目录已存在，忽略
                }
            }
            if (!classes.isEmpty()) {
                log.info("已检查 {} 个班级的文件目录", classes.size());
            }
        } catch (Exception e) {
            log.warn("检查班级目录时出错 (dufs 可能未启动): {}", e.getMessage());
        }
    }
}
