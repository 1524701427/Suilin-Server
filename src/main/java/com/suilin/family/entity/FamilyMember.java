package com.suilin.family.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("family_members")
public class FamilyMember {
    private Long id;
    private Long familyId;
    private Long userId;
    private String memberRole;
    private String status;
    private LocalDateTime createdAt;
}
