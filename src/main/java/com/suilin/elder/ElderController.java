package com.suilin.elder;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suilin.common.ApiResponse;
import com.suilin.elder.entity.Elder;
import com.suilin.elder.entity.ElderInvite;
import com.suilin.elder.mapper.ElderInviteMapper;
import com.suilin.elder.mapper.ElderMapper;
import com.suilin.family.FamilyAccessService;
import com.suilin.family.entity.Family;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ElderController {
    private final ElderMapper elderMapper;
    private final ElderInviteMapper inviteMapper;
    private final FamilyAccessService familyAccessService;
    private final ObjectMapper objectMapper;

    public record ElderRequest(@NotBlank String name, @NotBlank String relation, LocalDate birthday,
                               String phone, List<String> healthTags) {}
    public record BindRequest(@NotBlank String clientId) {}

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

    private String tagsJson(List<String> tags) {
        try { return objectMapper.writeValueAsString(tags == null ? List.of() : tags); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("健康标签格式不正确"); }
    }

    private Map<String,Object> view(Elder elder) {
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("id", elder.getId());
        data.put("familyId", elder.getFamilyId());
        data.put("name", elder.getName());
        data.put("relation", elder.getRelation());
        data.put("birthday", elder.getBirthday());
        data.put("age", elder.getBirthday() == null ? null : Math.max(0, Period.between(elder.getBirthday(), LocalDate.now()).getYears()));
        data.put("phone", elder.getPhone());
        data.put("healthTagsJson", elder.getHealthTagsJson());
        data.put("bindStatus", elder.getBindStatus());
        return data;
    }

    @PostMapping("/api/elders")
    @Transactional
    public ApiResponse<?> create(@Valid @RequestBody ElderRequest req) {
        long userId = familyAccessService.currentUserId();
        Family family = familyAccessService.currentFamily();
        Elder elder = new Elder();
        elder.setFamilyId(family.getId());
        elder.setCreatorUserId(userId);
        elder.setName(req.name().trim());
        elder.setRelation(req.relation().trim());
        elder.setBirthday(req.birthday());
        elder.setPhone(req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
        elder.setHealthTagsJson(tagsJson(req.healthTags()));
        elder.setBindStatus("WAITING");
        elder.setCreatedAt(LocalDateTime.now());
        elder.setUpdatedAt(LocalDateTime.now());
        elderMapper.insert(elder);
        ElderInvite invite = newInvite(elder.getId(), userId);
        return ApiResponse.ok(Map.of("elderId", elder.getId(), "inviteToken", invite.getInviteToken(), "bindStatus", elder.getBindStatus()));
    }

    @GetMapping("/api/elders")
    @Transactional
    public ApiResponse<?> list() {
        long userId = familyAccessService.currentUserId();
        Family family = familyAccessService.currentFamily();
        List<Elder> legacy = elderMapper.selectList(new LambdaQueryWrapper<Elder>()
                .isNull(Elder::getFamilyId).eq(Elder::getCreatorUserId, userId));
        for (Elder e : legacy) {
            e.setFamilyId(family.getId());
            e.setUpdatedAt(LocalDateTime.now());
            elderMapper.updateById(e);
        }
        List<Elder> elders = new ArrayList<>(elderMapper.selectList(new LambdaQueryWrapper<Elder>()
                .eq(Elder::getFamilyId, family.getId()).orderByDesc(Elder::getCreatedAt)));
        return ApiResponse.ok(elders.stream().map(this::view).toList());
    }

    @PutMapping("/api/elders/{elderId}")
    public ApiResponse<?> update(@PathVariable Long elderId, @Valid @RequestBody ElderRequest req) {
        Elder elder = familyAccessService.requireElderAccess(elderId);
        elder.setName(req.name().trim());
        elder.setRelation(req.relation().trim());
        elder.setBirthday(req.birthday());
        elder.setPhone(req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
        elder.setHealthTagsJson(tagsJson(req.healthTags()));
        elder.setUpdatedAt(LocalDateTime.now());
        elderMapper.updateById(elder);
        return ApiResponse.ok(view(elder));
    }

    @PostMapping("/api/elders/{elderId}/invite")
    @Transactional
    public ApiResponse<?> invite(@PathVariable Long elderId) {
        long userId = familyAccessService.currentUserId();
        Elder elder = familyAccessService.requireElderAccess(elderId);
        if ("BOUND".equals(elder.getBindStatus())) throw new IllegalArgumentException("长辈已完成绑定");
        ElderInvite existing = inviteMapper.selectOne(new LambdaQueryWrapper<ElderInvite>()
                .eq(ElderInvite::getElderId, elderId).eq(ElderInvite::getStatus, "WAITING")
                .gt(ElderInvite::getExpiresAt, LocalDateTime.now()).orderByDesc(ElderInvite::getCreatedAt).last("limit 1"));
        ElderInvite invite = existing != null ? existing : newInvite(elderId, userId);
        return ApiResponse.ok(Map.of("elderId", elderId, "inviteToken", invite.getInviteToken(), "expiresAt", invite.getExpiresAt()));
    }

    @PostMapping("/api/elders/{elderId}/unbind")
    @Transactional
    public ApiResponse<?> unbind(@PathVariable Long elderId) {
        Elder elder = familyAccessService.requireElderAccess(elderId);
        elder.setBindStatus("WAITING");
        elder.setBoundClientId(null);
        elder.setBoundClientToken(null);
        elder.setBoundAt(null);
        elder.setUpdatedAt(LocalDateTime.now());
        elderMapper.updateById(elder);
        return ApiResponse.ok(view(elder));
    }

    @GetMapping("/api/elder-invites/{token}")
    public ApiResponse<?> preview(@PathVariable String token) {
        ElderInvite invite = inviteMapper.selectOne(new LambdaQueryWrapper<ElderInvite>().eq(ElderInvite::getInviteToken, token));
        if (invite == null || !"WAITING".equals(invite.getStatus()) || invite.getExpiresAt().isBefore(LocalDateTime.now())) throw new IllegalArgumentException("邀请已失效");
        Elder elder = elderMapper.selectById(invite.getElderId());
        if (elder == null) throw new IllegalArgumentException("长辈资料不存在");
        return ApiResponse.ok(Map.of("elderId", elder.getId(), "name", elder.getName(), "relation", elder.getRelation(), "expiresAt", invite.getExpiresAt()));
    }

    @PostMapping("/api/elder-invites/{token}/accept")
    @Transactional
    public ApiResponse<?> accept(@PathVariable String token, @Valid @RequestBody BindRequest req) {
        ElderInvite invite = inviteMapper.selectOne(new LambdaQueryWrapper<ElderInvite>().eq(ElderInvite::getInviteToken, token));
        if (invite == null || !"WAITING".equals(invite.getStatus()) || invite.getExpiresAt().isBefore(LocalDateTime.now())) throw new IllegalArgumentException("邀请已失效");
        Elder elder = elderMapper.selectById(invite.getElderId());
        if (elder == null) throw new IllegalArgumentException("长辈资料不存在");
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
