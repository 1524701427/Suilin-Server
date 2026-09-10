package com.suilin.family;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.suilin.common.ApiResponse;
import com.suilin.family.entity.Family;
import com.suilin.family.entity.FamilyInvite;
import com.suilin.family.entity.FamilyMember;
import com.suilin.family.mapper.FamilyInviteMapper;
import com.suilin.family.mapper.FamilyMemberMapper;
import com.suilin.user.entity.User;
import com.suilin.user.mapper.UserMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class FamilyController {
    private final FamilyAccessService familyAccessService;
    private final FamilyMemberMapper memberMapper;
    private final FamilyInviteMapper inviteMapper;
    private final UserMapper userMapper;

    public record InviteRequest(
            @Pattern(regexp="^1\\d{10}$", message="手机号格式不正确") String phone,
            @NotBlank(message="成员角色不能为空") String memberRole) {}

    @GetMapping("/api/families/current")
    public ApiResponse<?> current() {
        Family family = familyAccessService.currentFamily();
        FamilyMember me = familyAccessService.requireMember(family.getId(), familyAccessService.currentUserId());
        return ApiResponse.ok(Map.of(
                "id", family.getId(),
                "name", family.getName(),
                "ownerUserId", family.getOwnerUserId(),
                "myRole", me.getMemberRole()
        ));
    }

    @GetMapping("/api/families/current/members")
    public ApiResponse<?> members() {
        Family family = familyAccessService.currentFamily();
        List<FamilyMember> members = memberMapper.selectList(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getFamilyId, family.getId())
                .eq(FamilyMember::getStatus, "ACTIVE")
                .orderByAsc(FamilyMember::getCreatedAt));
        return ApiResponse.ok(members.stream().map(m -> {
            User u = userMapper.selectById(m.getUserId());
            Map<String,Object> data = new LinkedHashMap<>();
            data.put("id", m.getId());
            data.put("userId", m.getUserId());
            data.put("name", u == null ? "" : u.getName());
            data.put("phone", u == null ? "" : u.getPhone());
            data.put("memberRole", m.getMemberRole());
            data.put("isOwner", family.getOwnerUserId().equals(m.getUserId()));
            return data;
        }).toList());
    }

    @PostMapping("/api/families/current/invites")
    @Transactional
    public ApiResponse<?> createInvite(@Valid @RequestBody InviteRequest req) {
        long uid = familyAccessService.currentUserId();
        Family family = familyAccessService.currentFamily();
        FamilyMember me = familyAccessService.requireMember(family.getId(), uid);
        if (!familyAccessService.canManageFamily(me)) throw new IllegalArgumentException("无权邀请家庭成员");
        if (!("CAREGIVER".equals(req.memberRole()) || "MEMBER".equals(req.memberRole()) || "EMERGENCY_CONTACT".equals(req.memberRole()))) {
            throw new IllegalArgumentException("无效的成员角色");
        }
        User target = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, req.phone()));
        if (target != null && memberMapper.selectCount(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getFamilyId, family.getId()).eq(FamilyMember::getUserId, target.getId()).eq(FamilyMember::getStatus, "ACTIVE")) > 0) {
            throw new IllegalArgumentException("该用户已经在家庭中");
        }
        inviteMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<FamilyInvite>()
                .eq(FamilyInvite::getFamilyId, family.getId())
                .eq(FamilyInvite::getInvitePhone, req.phone())
                .eq(FamilyInvite::getStatus, "WAITING")
                .set(FamilyInvite::getStatus, "CANCELLED"));
        FamilyInvite invite = new FamilyInvite();
        invite.setFamilyId(family.getId());
        invite.setInviterUserId(uid);
        invite.setInvitePhone(req.phone());
        invite.setMemberRole(req.memberRole());
        invite.setInviteToken(UUID.randomUUID().toString().replace("-", ""));
        invite.setStatus("WAITING");
        invite.setExpiresAt(LocalDateTime.now().plusDays(7));
        invite.setCreatedAt(LocalDateTime.now());
        inviteMapper.insert(invite);
        return ApiResponse.ok(Map.of("inviteToken", invite.getInviteToken(), "expiresAt", invite.getExpiresAt(), "phone", invite.getInvitePhone()));
    }

    @GetMapping("/api/family-invites/{token}")
    public ApiResponse<?> preview(@PathVariable String token) {
        FamilyInvite invite = requireValidInvite(token);
        User inviter = userMapper.selectById(invite.getInviterUserId());
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("phone", invite.getInvitePhone());
        data.put("memberRole", invite.getMemberRole());
        data.put("inviterName", inviter == null ? "家人" : inviter.getName());
        data.put("expiresAt", invite.getExpiresAt());
        return ApiResponse.ok(data);
    }

    @PostMapping("/api/family-invites/{token}/accept")
    @Transactional
    public ApiResponse<?> accept(@PathVariable String token) {
        StpUtil.checkLogin();
        long uid = StpUtil.getLoginIdAsLong();
        FamilyInvite invite = requireValidInvite(token);
        User user = userMapper.selectById(uid);
        if (user == null || !invite.getInvitePhone().equals(user.getPhone())) {
            throw new IllegalArgumentException("请使用被邀请的手机号登录后接受邀请");
        }
        FamilyMember existing = memberMapper.selectOne(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getFamilyId, invite.getFamilyId())
                .eq(FamilyMember::getUserId, uid)
                .last("limit 1"));
        if (existing == null) {
            FamilyMember member = new FamilyMember();
            member.setFamilyId(invite.getFamilyId());
            member.setUserId(uid);
            member.setMemberRole(invite.getMemberRole());
            member.setStatus("ACTIVE");
            member.setCreatedAt(LocalDateTime.now());
            memberMapper.insert(member);
        } else if (!"ACTIVE".equals(existing.getStatus())) {
            existing.setStatus("ACTIVE");
            existing.setMemberRole(invite.getMemberRole());
            memberMapper.updateById(existing);
        }
        invite.setStatus("ACCEPTED");
        invite.setAcceptedByUserId(uid);
        invite.setAcceptedAt(LocalDateTime.now());
        inviteMapper.updateById(invite);
        return ApiResponse.ok(Map.of("familyId", invite.getFamilyId(), "memberRole", invite.getMemberRole()));
    }

    @DeleteMapping("/api/families/current/members/{memberId}")
    public ApiResponse<Void> removeMember(@PathVariable Long memberId) {
        long uid = familyAccessService.currentUserId();
        Family family = familyAccessService.currentFamily();
        FamilyMember me = familyAccessService.requireMember(family.getId(), uid);
        if (!familyAccessService.canManageFamily(me)) throw new IllegalArgumentException("无权移除家庭成员");
        FamilyMember target = memberMapper.selectById(memberId);
        if (target == null || !family.getId().equals(target.getFamilyId())) throw new IllegalArgumentException("成员不存在");
        if (family.getOwnerUserId().equals(target.getUserId())) throw new IllegalArgumentException("不能移除家庭创建者");
        target.setStatus("REMOVED");
        memberMapper.updateById(target);
        return ApiResponse.ok();
    }

    private FamilyInvite requireValidInvite(String token) {
        FamilyInvite invite = inviteMapper.selectOne(new LambdaQueryWrapper<FamilyInvite>().eq(FamilyInvite::getInviteToken, token));
        if (invite == null || !"WAITING".equals(invite.getStatus()) || invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("家庭邀请已失效");
        }
        return invite;
    }
}
