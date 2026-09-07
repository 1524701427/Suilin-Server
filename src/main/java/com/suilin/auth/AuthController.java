package com.suilin.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.suilin.common.ApiResponse;
import com.suilin.user.entity.User;
import com.suilin.user.mapper.UserMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public record RegisterRequest(@Pattern(regexp="^1\\d{10}$", message="手机号格式不正确") String phone,
                                  @NotBlank(message="姓名不能为空") String name,
                                  @Size(min=6,max=64,message="密码至少6位") String password) {}
    public record LoginRequest(@Pattern(regexp="^1\\d{10}$", message="手机号格式不正确") String phone,
                               @NotBlank(message="密码不能为空") String password) {}

    @PostMapping("/register")
    public ApiResponse<?> register(@Valid @RequestBody RegisterRequest req) {
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getPhone, req.phone())) > 0) {
            throw new IllegalArgumentException("手机号已注册");
        }
        User u = new User();
        u.setPhone(req.phone()); u.setName(req.name().trim()); u.setPasswordHash(encoder.encode(req.password()));
        u.setRole("FAMILY"); u.setStatus("ACTIVE"); u.setCreatedAt(LocalDateTime.now()); u.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(u);
        StpUtil.login(u.getId());
        return ApiResponse.ok(Map.of("userId", u.getId(), "token", StpUtil.getTokenValue(), "role", u.getRole()));
    }

    @PostMapping("/login")
    public ApiResponse<?> login(@Valid @RequestBody LoginRequest req) {
        User u = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, req.phone()));
        if (u == null || !encoder.matches(req.password(), u.getPasswordHash())) throw new IllegalArgumentException("手机号或密码错误");
        if (!"ACTIVE".equals(u.getStatus())) throw new IllegalArgumentException("账号不可用");
        StpUtil.login(u.getId());
        return ApiResponse.ok(Map.of("userId", u.getId(), "token", StpUtil.getTokenValue(), "name", u.getName(), "role", u.getRole()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() { StpUtil.logout(); return ApiResponse.ok(); }
}
