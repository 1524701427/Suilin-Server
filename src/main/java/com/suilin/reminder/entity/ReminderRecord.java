package com.suilin.reminder.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("reminder_records")
public class ReminderRecord {
    private Long id;
    private Long reminderId;
    private Long elderId;
    private LocalDateTime scheduledAt;
    private String status;
    private LocalDateTime completedAt;
    private String sourceType;
    private LocalDateTime createdAt;
}
