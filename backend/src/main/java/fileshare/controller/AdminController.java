package fileshare.controller;

import fileshare.dto.ApiResponse;
import fileshare.entity.Clazz;
import fileshare.entity.User;
import fileshare.entity.UserClass;
import fileshare.repository.ClazzRepository;
import fileshare.repository.UserClassRepository;
import fileshare.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final ClazzRepository clazzRepository;
    private final UserClassRepository userClassRepository;

    public AdminController(UserRepository userRepository, ClazzRepository clazzRepository,
                           UserClassRepository userClassRepository) {
        this.userRepository = userRepository;
        this.clazzRepository = clazzRepository;
        this.userClassRepository = userClassRepository;
    }

    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> allUsers() {
        List<Map<String, Object>> result = userRepository.findAll().stream().map(u -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("username", u.getUsername());
            map.put("role", u.getRole().name());
            map.put("createdAt", u.getCreatedAt().toString());
            map.put("classCount", userClassRepository.findByUserId(u.getId()).size());
            return map;
        }).collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<Void> deleteUser(@AuthenticationPrincipal User current, @PathVariable Long id) {
        if (current.getId().equals(id)) {
            throw new IllegalArgumentException("不能删除自己");
        }
        userRepository.deleteById(id);
        return ApiResponse.success("用户已删除", null);
    }

    @GetMapping("/classes")
    public ApiResponse<List<Map<String, Object>>> allClasses() {
        List<Map<String, Object>> result = clazzRepository.findAll().stream().map(c -> {
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
}
