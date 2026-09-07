package com.suilin.sos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sos_events")
public class SosEvent {
    private Long id;
    private Long elderId;
    private String sourceType;
    private String sourceRef;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
}
