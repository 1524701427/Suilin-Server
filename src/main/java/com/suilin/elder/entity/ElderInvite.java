package com.suilin.elder.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("elder_invites")
public class ElderInvite {
    private Long id;
    private Long elderId;
    private Long inviterUserId;
    private String inviteToken;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime createdAt;
}
