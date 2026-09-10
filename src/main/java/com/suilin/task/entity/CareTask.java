package com.suilin.task.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("care_tasks")
public class CareTask {
    private Long id;
    private Long familyId;
    private Long elderId;
    private String title;
    private Long assigneeUserId;
    private LocalDateTime dueAt;
    private String note;
    private String status;
    private Long createdByUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
