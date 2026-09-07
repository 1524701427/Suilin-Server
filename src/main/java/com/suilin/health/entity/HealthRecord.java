package com.suilin.health.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("health_records")
public class HealthRecord { private Long id; private Long elderId; private String metricType; private String valueText; private String unit; private String sourceType; private String sourceRef; private Long recordedByUserId; private LocalDateTime measuredAt; private LocalDateTime createdAt; }
