package com.suilin.health;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.suilin.common.ApiResponse;
import com.suilin.elder.entity.Elder;
import com.suilin.elder.mapper.ElderMapper;
import com.suilin.health.entity.HealthRecord;
import com.suilin.health.mapper.HealthRecordMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/elders/{elderId}/health-records")
@RequiredArgsConstructor
public class HealthController {
    private final HealthRecordMapper healthMapper;
    private final ElderMapper elderMapper;

    public record Request(@NotBlank String metricType, @NotBlank String valueText, String unit,
                          @NotBlank String sourceType, String sourceRef, @NotNull LocalDateTime measuredAt) {}

    private void assertOwner(Long elderId, long uid) {
        Elder elder = elderMapper.selectById(elderId);
        if (elder == null || !Long.valueOf(uid).equals(elder.getCreatorUserId())) throw new IllegalArgumentException("无权访问该长辈");
    }

    @GetMapping
    public ApiResponse<?> list(@PathVariable Long elderId, @RequestParam(required=false) String metricType) {
        long uid = StpUtil.getLoginIdAsLong(); assertOwner(elderId, uid);
        LambdaQueryWrapper<HealthRecord> q = new LambdaQueryWrapper<HealthRecord>().eq(HealthRecord::getElderId, elderId);
        if (metricType != null && !metricType.isBlank()) q.eq(HealthRecord::getMetricType, metricType);
        q.orderByDesc(HealthRecord::getMeasuredAt).last("limit 100");
        return ApiResponse.ok(healthMapper.selectList(q));
    }

    @PostMapping
    public ApiResponse<?> create(@PathVariable Long elderId, @Valid @RequestBody Request req) {
        long uid = StpUtil.getLoginIdAsLong(); assertOwner(elderId, uid);
        if (!(req.sourceType().equals("FAMILY_MANUAL") || req.sourceType().equals("ELDER_MANUAL") || req.sourceType().equals("DEVICE") || req.sourceType().equals("HOSPITAL"))) {
            throw new IllegalArgumentException("无效的数据来源");
        }
        HealthRecord r = new HealthRecord();
        r.setElderId(elderId); r.setMetricType(req.metricType()); r.setValueText(req.valueText()); r.setUnit(req.unit());
        r.setSourceType(req.sourceType()); r.setSourceRef(req.sourceRef()); r.setRecordedByUserId(uid); r.setMeasuredAt(req.measuredAt()); r.setCreatedAt(LocalDateTime.now());
        healthMapper.insert(r); return ApiResponse.ok(r);
    }
}
