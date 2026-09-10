package com.suilin.feedback.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("feedbacks")
public class Feedback {
    private Long id;
    private Long userId;
    private String content;
    private String contact;
    private String status;
    private LocalDateTime createdAt;
}
