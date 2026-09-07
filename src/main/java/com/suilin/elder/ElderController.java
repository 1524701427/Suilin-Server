package com.suilin.elder;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.suilin.common.ApiResponse;
import com.suilin.elder.entity.Elder;
import com.suilin.elder.entity.ElderInvite;
import com.suilin.elder.mapper.ElderInviteMapper;
import com.suilin.elder.mapper.ElderMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ElderController {
    private final ElderMapper elderMapper;
    private final ElderInviteMapper inviteMapper;

    public record CreateElderRequest(@NotBlank String name, @NotBlank String relation, @NotNull LocalDate birthday,
                                     String phone, List<String> healthTags) {}
    public record BindRequest(@NotBlank String clientId) {}

    private Elder requireOwner(Long elderId, long userId) {
        Elder elder = elderMapper.selectById(elderId);
        if (elder == null || !Long.valueOf(userId).equals(elder.getCreatorUserId())) {
            throw new IllegalArgumentException("无权访问该长辈");
        }
        return elder;
    }

    private ElderInvite newInvite(Long elderId, long userId) {
        ElderInvite invite = new ElderInvite();
        invite.setElderId(elderId);
        invite.setInviterUserId(userId);
        invite.setInviteToken(UUID.randomUUID().toString().replace("-", ""));
        invite.setStatus("WAITING");
        invite.setExpiresAt(LocalDateTime.now().plusDays(7));
        invite.setCreatedAt(LocalDateTime.now());
        inviteMapper.insert(invite);
        return invite;
    }

    @PostMapping("/api/elders")
    @Transactional
    public ApiResponse<?> create(@Valid @RequestBody CreateElderRequest req) {
        long userId = StpUtil.getLoginIdAsLong();
        Elder elder = new Elder();
        elder.setCreatorUserId(userId);
        elder.setName(req.name().trim());
        elder.setRelation(req.relation());
        elder.setBirthday(req.birthday());
        elder.setPhone(req.phone());
        elder.setHealthTagsJson(req.healthTags() == null ? "[]" : "[\"" + String.join("\",\"", req.healthTags()) + "\"]");
        elder.setBindStatus("WAITING");
        elder.setCreatedAt(LocalDateTime.now());
        elder.setUpdatedAt(LocalDateTime.now());
        elderMapper.insert(elder);

        ElderInvite invite = newInvite(elder.getId(), userId);
        return ApiResponse.ok(Map.of("elderId", elder.getId(), "inviteToken", invite.getInviteToken(), "bindStatus", elder.getBindStatus()));
    }

    @GetMapping("/api/elders")
    public ApiResponse<?> list() {
        long userId = StpUtil.getLoginIdAsLong();
        List<Elder> elders = elderMapper.selectList(new LambdaQueryWrapper<Elder>()
                .eq(Elder::getCreatorUserId, userId)
                .orderByDesc(Elder::getCreatedAt));
        return ApiResponse.ok(elders.stream().map(elder -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", elder.getId());
            data.put("name", elder.getName());
            data.put("relation", elder.getRelation());
            data.put("birthday", elder.getBirthday());
            data.put("age", Period.between(elder.getBirthday(), LocalDate.now()).getYears());
            data.put("phone", elder.getPhone());
            data.put("bindStatus", elder.getBindStatus());
            return data;
        }).toList());
    }

    @PostMapping("/api/elders/{elderId}/invite")
    @Transactional
    public ApiResponse<?> invite(@PathVariable Long elderId) {
        long userId = StpUtil.getLoginIdAsLong();
        Elder elder = requireOwner(elderId, userId);
        if ("BOUND".equals(elder.getBindStatus())) throw new IllegalArgumentException("长辈已完成绑定");

        ElderInvite existing = inviteMapper.selectOne(new LambdaQueryWrapper<ElderInvite>()
                .eq(ElderInvite::getElderId, elderId)
                .eq(ElderInvite::getStatus, "WAITING")
                .gt(ElderInvite::getExpiresAt, LocalDateTime.now())
                .orderByDesc(ElderInvite::getCreatedAt)
                .last("limit 1"));
        ElderInvite invite = existing != null ? existing : newInvite(elderId, userId);
        return ApiResponse.ok(Map.of("elderId", elderId, "inviteToken", invite.getInviteToken(), "expiresAt", invite.getExpiresAt()));
    }

    @GetMapping("/api/elder-invites/{token}")
    public ApiResponse<?> preview(@PathVariable String token) {
        ElderInvite invite = inviteMapper.selectOne(new LambdaQueryWrapper<ElderInvite>().eq(ElderInvite::getInviteToken, token));
        if (invite == null || !"WAITING".equals(invite.getStatus()) || invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("邀请已失效");
        }
        Elder elder = elderMapper.selectById(invite.getElderId());
        return ApiResponse.ok(Map.of("elderId", elder.getId(), "name", elder.getName(), "relation", elder.getRelation(), "expiresAt", invite.getExpiresAt()));
    }

    @PostMapping("/api/elder-invites/{token}/accept")
    @Transactional
    public ApiResponse<?> accept(@PathVariable String token, @Valid @RequestBody BindRequest req) {
        ElderInvite invite = inviteMapper.selectOne(new LambdaQueryWrapper<ElderInvite>().eq(ElderInvite::getInviteToken, token));
        if (invite == null || !"WAITING".equals(invite.getStatus()) || invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("邀请已失效");
        }
        Elder elder = elderMapper.selectById(invite.getElderId());
        String clientToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        elder.setBindStatus("BOUND");
        elder.setBoundClientId(req.clientId());
        elder.setBoundClientToken(clientToken);
        elder.setBoundAt(LocalDateTime.now());
        elder.setUpdatedAt(LocalDateTime.now());
        elderMapper.updateById(elder);

        invite.setStatus("ACCEPTED");
        invite.setAcceptedAt(LocalDateTime.now());
        inviteMapper.updateById(invite);
        return ApiResponse.ok(Map.of("elderId", elder.getId(), "bindStatus", elder.getBindStatus(), "clientToken", clientToken));
    }
}
