package com.suilin.notification.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("notification_settings")
public class NotificationSetting {
    private Long id;
    private Long userId;
    private Boolean sosEnabled;
    private Boolean reminderEnabled;
    private Boolean healthEnabled;
    private Boolean deviceEnabled;
    private Boolean serviceEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
