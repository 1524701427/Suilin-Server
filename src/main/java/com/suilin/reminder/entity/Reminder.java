package com.suilin.reminder.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("reminders")
public class Reminder { private Long id; private Long elderId; private Long createdByUserId; private String title; private String type; private String scheduleTime; private String dosage; private String repeatRule; private Integer notifyAfterMinutes; private Boolean enabled; private LocalDateTime createdAt; private LocalDateTime updatedAt; }
