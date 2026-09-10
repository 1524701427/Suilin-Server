package com.suilin.health;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.suilin.common.ApiResponse;
import com.suilin.family.FamilyAccessService;
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
    private final FamilyAccessService familyAccessService;

    public record Request(@NotBlank String metricType, @NotBlank String valueText, String unit,
                          @NotBlank String sourceType, String sourceRef, @NotNull LocalDateTime measuredAt) {}

    @GetMapping
    public ApiResponse<?> list(@PathVariable Long elderId, @RequestParam(required=false) String metricType) {
        familyAccessService.requireElderAccess(elderId);
        LambdaQueryWrapper<HealthRecord> q = new LambdaQueryWrapper<HealthRecord>().eq(HealthRecord::getElderId, elderId);
        if (metricType != null && !metricType.isBlank()) q.eq(HealthRecord::getMetricType, metricType);
        q.orderByDesc(HealthRecord::getMeasuredAt).last("limit 100");
        return ApiResponse.ok(healthMapper.selectList(q));
    }

    @PostMapping
    public ApiResponse<?> create(@PathVariable Long elderId, @Valid @RequestBody Request req) {
        familyAccessService.requireElderAccess(elderId);
        HealthRecord r = new HealthRecord();
        r.setElderId(elderId);
        apply(r, req);
        r.setRecordedByUserId(familyAccessService.currentUserId());
        r.setCreatedAt(LocalDateTime.now());
        healthMapper.insert(r);
        return ApiResponse.ok(r);
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long elderId, @PathVariable Long id, @Valid @RequestBody Request req) {
        familyAccessService.requireElderAccess(elderId);
        HealthRecord r = requireRecord(elderId, id);
        if (!"FAMILY_MANUAL".equals(r.getSourceType())) throw new IllegalArgumentException("只有家人手工录入的数据可以修改");
        apply(r, req);
        r.setRecordedByUserId(familyAccessService.currentUserId());
        healthMapper.updateById(r);
        return ApiResponse.ok(r);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long elderId, @PathVariable Long id) {
        familyAccessService.requireElderAccess(elderId);
        HealthRecord r = requireRecord(elderId, id);
        if (!"FAMILY_MANUAL".equals(r.getSourceType())) throw new IllegalArgumentException("只有家人手工录入的数据可以删除");
        healthMapper.deleteById(id);
        return ApiResponse.ok();
    }

    private HealthRecord requireRecord(Long elderId, Long id) {
        HealthRecord r = healthMapper.selectById(id);
        if (r == null || !elderId.equals(r.getElderId())) throw new IllegalArgumentException("健康记录不存在");
        return r;
    }

    private void apply(HealthRecord r, Request req) {
        if (!(req.sourceType().equals("FAMILY_MANUAL") || req.sourceType().equals("ELDER_MANUAL") || req.sourceType().equals("DEVICE") || req.sourceType().equals("HOSPITAL"))) {
            throw new IllegalArgumentException("无效的数据来源");
        }
        r.setMetricType(req.metricType().trim());
        r.setValueText(req.valueText().trim());
        r.setUnit(req.unit() == null || req.unit().isBlank() ? null : req.unit().trim());
        r.setSourceType(req.sourceType());
        r.setSourceRef(req.sourceRef() == null || req.sourceRef().isBlank() ? null : req.sourceRef().trim());
        r.setMeasuredAt(req.measuredAt());
    }
}
