package com.suilin.family;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.suilin.elder.entity.Elder;
import com.suilin.elder.mapper.ElderMapper;
import com.suilin.family.entity.Family;
import com.suilin.family.entity.FamilyMember;
import com.suilin.family.mapper.FamilyMapper;
import com.suilin.family.mapper.FamilyMemberMapper;
import com.suilin.user.entity.User;
import com.suilin.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FamilyAccessService {
    private final FamilyMapper familyMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final ElderMapper elderMapper;
    private final UserMapper userMapper;

    public long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    @Transactional
    public Family currentFamily() {
        long uid = currentUserId();
        FamilyMember member = familyMemberMapper.selectOne(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getUserId, uid)
                .eq(FamilyMember::getStatus, "ACTIVE")
                .orderByAsc(FamilyMember::getCreatedAt)
                .last("limit 1"));
        if (member != null) {
            Family family = familyMapper.selectById(member.getFamilyId());
            if (family != null) return family;
        }

        User user = userMapper.selectById(uid);
        if (user == null) throw new IllegalArgumentException("用户不存在");

        LocalDateTime now = LocalDateTime.now();
        Family family = new Family();
        family.setName((user.getName() == null || user.getName().isBlank() ? "我的" : user.getName().trim()) + "的家庭");
        family.setOwnerUserId(uid);
        family.setCreatedAt(now);
        family.setUpdatedAt(now);
        familyMapper.insert(family);

        FamilyMember owner = new FamilyMember();
        owner.setFamilyId(family.getId());
        owner.setUserId(uid);
        owner.setMemberRole("OWNER");
        owner.setStatus("ACTIVE");
        owner.setCreatedAt(now);
        familyMemberMapper.insert(owner);
        return family;
    }

    public FamilyMember requireMember(Long familyId, long userId) {
        FamilyMember member = familyMemberMapper.selectOne(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getFamilyId, familyId)
                .eq(FamilyMember::getUserId, userId)
                .eq(FamilyMember::getStatus, "ACTIVE")
                .last("limit 1"));
        if (member == null) throw new IllegalArgumentException("无权访问该家庭");
        return member;
    }

    public Elder requireElderAccess(Long elderId) {
        long uid = currentUserId();
        Elder elder = elderMapper.selectById(elderId);
        if (elder == null) throw new IllegalArgumentException("长辈不存在");
        if (elder.getFamilyId() != null) {
            requireMember(elder.getFamilyId(), uid);
        } else if (!Long.valueOf(uid).equals(elder.getCreatorUserId())) {
            throw new IllegalArgumentException("无权访问该长辈");
        }
        return elder;
    }

    public boolean canManageFamily(FamilyMember member) {
        return member != null && ("OWNER".equals(member.getMemberRole()) || "CAREGIVER".equals(member.getMemberRole()));
    }
}
