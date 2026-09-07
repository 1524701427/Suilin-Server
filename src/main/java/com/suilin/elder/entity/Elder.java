package com.suilin.elder.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("elders")
public class Elder {
    private Long id;
    private Long creatorUserId;
    private String name;
    private String relation;
    private LocalDate birthday;
    private String phone;
    private String healthTagsJson;
    private String bindStatus;
    private String boundClientId;
    private String boundClientToken;
    private LocalDateTime boundAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
