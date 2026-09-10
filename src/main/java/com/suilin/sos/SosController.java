package com.suilin.sos;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.suilin.common.ApiResponse;
import com.suilin.elder.entity.Elder;
import com.suilin.elder.mapper.ElderMapper;
import com.suilin.family.FamilyAccessService;
import com.suilin.family.entity.Family;
import com.suilin.sos.entity.SosEvent;
import com.suilin.sos.mapper.SosEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sos-events")
@RequiredArgsConstructor
public class SosController {
    private final SosEventMapper sosMapper;
    private final ElderMapper elderMapper;
    private final FamilyAccessService familyAccessService;

    @GetMapping
    public ApiResponse<?> list() {
        Family family = familyAccessService.currentFamily();
        long uid = familyAccessService.currentUserId();
        List<Elder> elders = new ArrayList<>(elderMapper.selectList(new LambdaQueryWrapper<Elder>().eq(Elder::getFamilyId, family.getId())));
        List<Elder> legacy = elderMapper.selectList(new LambdaQueryWrapper<Elder>().isNull(Elder::getFamilyId).eq(Elder::getCreatorUserId, uid));
        for (Elder e : legacy) if (elders.stream().noneMatch(x -> x.getId().equals(e.getId()))) elders.add(e);
        if (elders.isEmpty()) return ApiResponse.ok(List.of());
        List<Long> elderIds = elders.stream().map(Elder::getId).toList();
        List<SosEvent> events = sosMapper.selectList(new LambdaQueryWrapper<SosEvent>()
                .in(SosEvent::getElderId, elderIds)
                .orderByDesc(SosEvent::getCreatedAt)
                .last("limit 100"));
        return ApiResponse.ok(events.stream().map(e -> view(e, elders)).toList());
    }

    @PostMapping("/{id}/handle")
    public ApiResponse<?> handle(@PathVariable Long id) {
        SosEvent event = requireEvent(id);
        if ("CLOSED".equals(event.getStatus())) throw new IllegalArgumentException("该求助已关闭");
        event.setStatus("HANDLING");
        event.setHandledByUserId(familyAccessService.currentUserId());
        event.setHandledAt(LocalDateTime.now());
        sosMapper.updateById(event);
        return ApiResponse.ok(view(event, List.of(familyAccessService.requireElderAccess(event.getElderId()))));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<?> close(@PathVariable Long id) {
        SosEvent event = requireEvent(id);
        event.setStatus("CLOSED");
        if (event.getHandledByUserId() == null) event.setHandledByUserId(familyAccessService.currentUserId());
        if (event.getHandledAt() == null) event.setHandledAt(LocalDateTime.now());
        event.setClosedAt(LocalDateTime.now());
        sosMapper.updateById(event);
        return ApiResponse.ok(view(event, List.of(familyAccessService.requireElderAccess(event.getElderId()))));
    }

    private SosEvent requireEvent(Long id) {
        SosEvent event = sosMapper.selectById(id);
        if (event == null) throw new IllegalArgumentException("求助事件不存在");
        familyAccessService.requireElderAccess(event.getElderId());
        return event;
    }

    private Map<String,Object> view(SosEvent event, List<Elder> elders) {
        Elder elder = elders.stream().filter(e -> e.getId().equals(event.getElderId())).findFirst().orElse(null);
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("id", event.getId());
        data.put("elderId", event.getElderId());
        data.put("elderName", elder == null ? "" : elder.getName());
        data.put("status", event.getStatus());
        data.put("latitude", event.getLatitude());
        data.put("longitude", event.getLongitude());
        data.put("createdAt", event.getCreatedAt());
        data.put("handledByUserId", event.getHandledByUserId());
        data.put("handledAt", event.getHandledAt());
        data.put("closedAt", event.getClosedAt());
        return data;
    }
}
