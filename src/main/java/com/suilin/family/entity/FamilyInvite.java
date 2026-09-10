package com.suilin.family.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("family_invites")
public class FamilyInvite {
    private Long id;
    private Long familyId;
    private Long inviterUserId;
    private String invitePhone;
    private String memberRole;
    private String inviteToken;
    private String status;
    private LocalDateTime expiresAt;
    private Long acceptedByUserId;
    private LocalDateTime acceptedAt;
    private LocalDateTime createdAt;
}
