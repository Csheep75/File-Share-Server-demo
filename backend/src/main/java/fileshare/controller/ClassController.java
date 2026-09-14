package fileshare.controller;

import fileshare.dto.ApiResponse;
import fileshare.entity.Clazz;
import fileshare.entity.User;
import fileshare.entity.UserClass;
import fileshare.repository.ClazzRepository;
import fileshare.repository.UserClassRepository;
import fileshare.repository.UserRepository;
import fileshare.service.DufsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/classes")
public class ClassController {

    private final ClazzRepository clazzRepository;
    private final UserClassRepository userClassRepository;
    private final UserRepository userRepository;
    private final DufsService dufsService;

    public ClassController(ClazzRepository clazzRepository, UserClassRepository userClassRepository,
                           UserRepository userRepository, DufsService dufsService) {
        this.clazzRepository = clazzRepository;
        this.userClassRepository = userClassRepository;
        this.userRepository = userRepository;
        this.dufsService = dufsService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> myClasses(@AuthenticationPrincipal User user) {
        List<Clazz> classes;
        if (user.getRole() == User.Role.ADMIN) {
            classes = clazzRepository.findAll();
        } else {
            List<UserClass> ucs = userClassRepository.findByUserId(user.getId());
            List<Long> classIds = ucs.stream().map(UserClass::getClassId).collect(Collectors.toList());
            classes = clazzRepository.findAllById(classIds);
        }
        List<Map<String, Object>> result = classes.stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", c.getId());
            map.put("name", c.getName());
            map.put("inviteCode", c.getInviteCode());
            map.put("createdAt", c.getCreatedAt().toString());
            map.put("memberCount", userClassRepository.findByClassId(c.getId()).size());
            return map;
        }).collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@AuthenticationPrincipal User user,
                                                    @RequestBody Map<String, String> body) {
        if (user.getRole() != User.Role.ADMIN) {
            throw new IllegalArgumentException("只有管理员可以创建班级");
        }
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("班级名称不能为空");
        }
        Clazz clazz = new Clazz();
        clazz.setName(name);
        clazz.setInviteCode(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        clazz.setCreatedAt(Instant.now());
        clazzRepository.save(clazz);

        // 在 dufs 中创建对应的班级目录
        try {
            dufsService.mkdir("/" + clazz.getId());
        } catch (Exception e) {
            // 目录可能已存在，忽略错误
        }

        // 创建者自动加入班级
        UserClass uc = new UserClass();
        uc.setUserId(user.getId());
        uc.setClassId(clazz.getId());
        uc.setJoinedAt(Instant.now());
        userClassRepository.save(uc);

        Map<String, Object> result = new HashMap<>();
        result.put("id", clazz.getId());
        result.put("name", clazz.getName());
        result.put("inviteCode", clazz.getInviteCode());
        result.put("createdAt", clazz.getCreatedAt().toString());
        return ApiResponse.success(result);
    }

    @PostMapping("/join")
    public ApiResponse<Map<String, Object>> join(@AuthenticationPrincipal User user,
                                                  @RequestBody Map<String, String> body) {
        String inviteCode = body.get("inviteCode");
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new IllegalArgumentException("邀请码不能为空");
        }
        Clazz clazz = clazzRepository.findByInviteCode(inviteCode.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("邀请码无效"));

        if (userClassRepository.existsByUserIdAndClassId(user.getId(), clazz.getId())) {
            throw new IllegalArgumentException("你已加入该班级");
        }

        UserClass uc = new UserClass();
        uc.setUserId(user.getId());
        uc.setClassId(clazz.getId());
        uc.setJoinedAt(Instant.now());
        userClassRepository.save(uc);

        Map<String, Object> result = new HashMap<>();
        result.put("id", clazz.getId());
        result.put("name", clazz.getName());
        result.put("inviteCode", clazz.getInviteCode());
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<Map<String, Object>>> members(@AuthenticationPrincipal User user,
                                                           @PathVariable Long id) {
        List<UserClass> ucs = userClassRepository.findByClassId(id);
        List<Map<String, Object>> result = ucs.stream().map(uc -> {
            Map<String, Object> map = new HashMap<>();
            map.put("userId", uc.getUserId());
            map.put("joinedAt", uc.getJoinedAt().toString());
            userRepository.findById(uc.getUserId()).ifPresent(u -> {
                map.put("username", u.getUsername());
                map.put("role", u.getRole().name());
            });
            return map;
        }).collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ApiResponse<Void> removeMember(@AuthenticationPrincipal User user,
                                           @PathVariable Long id, @PathVariable Long userId) {
        if (user.getRole() != User.Role.ADMIN) {
            throw new IllegalArgumentException("只有管理员可以移除成员");
        }
        userClassRepository.deleteByUserIdAndClassId(userId, id);
        return ApiResponse.success("已移除", null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteClass(@AuthenticationPrincipal User user, @PathVariable Long id) {
        if (user.getRole() != User.Role.ADMIN) {
            throw new IllegalArgumentException("只有管理员可以删除班级");
        }
        userClassRepository.deleteByClassId(id);
        // 删除 dufs 中对应的班级目录
        try {
            dufsService.delete("/" + id);
        } catch (Exception e) {
            // 忽略
        }
        clazzRepository.deleteById(id);
        return ApiResponse.success("班级已删除", null);
    }
}
