package com.suilin.user;

import cn.dev33.satoken.stp.StpUtil;
import com.suilin.common.ApiResponse;
import com.suilin.user.entity.User;
import com.suilin.user.mapper.UserMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class UserController {
    private final UserMapper userMapper;

    public record UpdateRequest(@NotBlank @Size(max=50) String name) {}

    @GetMapping
    public ApiResponse<?> me() {
        User user = userMapper.selectById(StpUtil.getLoginIdAsLong());
        if (user == null) throw new IllegalArgumentException("用户不存在");
        return ApiResponse.ok(Map.of(
                "id", user.getId(),
                "name", user.getName(),
                "phone", user.getPhone(),
                "role", user.getRole()
        ));
    }

    @PutMapping
    public ApiResponse<?> update(@Valid @RequestBody UpdateRequest req) {
        User user = userMapper.selectById(StpUtil.getLoginIdAsLong());
        if (user == null) throw new IllegalArgumentException("用户不存在");
        user.setName(req.name().trim());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return ApiResponse.ok(Map.of("id", user.getId(), "name", user.getName(), "phone", user.getPhone(), "role", user.getRole()));
    }
}
