package com.suilin.privacy.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("privacy_settings")
public class PrivacySetting {
    private Long id;
    private Long userId;
    private Boolean healthVisible;
    private Boolean locationVisible;
    private Boolean deviceVisible;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
