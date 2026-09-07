package com.suilin.device.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("devices")
public class Device {
    private Long id;
    private Long elderId;
    private String deviceType;
    private String deviceSn;
    private String status;
    private LocalDateTime lastOnlineAt;
    private LocalDateTime createdAt;
}
