package com.suilin.reminder;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.suilin.common.ApiResponse;
import com.suilin.family.FamilyAccessService;
import com.suilin.reminder.entity.Reminder;
import com.suilin.reminder.mapper.ReminderMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/elders/{elderId}/reminders")
@RequiredArgsConstructor
public class ReminderController {
    private final ReminderMapper reminderMapper;
    private final FamilyAccessService familyAccessService;

    public record Request(@NotBlank String title, @NotBlank String type, @NotBlank String scheduleTime,
                          String dosage, String repeatRule, Integer notifyAfterMinutes, @NotNull Boolean enabled) {}

    @GetMapping
    public ApiResponse<?> list(@PathVariable Long elderId) {
        familyAccessService.requireElderAccess(elderId);
        return ApiResponse.ok(reminderMapper.selectList(new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getElderId, elderId).orderByAsc(Reminder::getScheduleTime)));
    }

    @PostMapping
    public ApiResponse<?> create(@PathVariable Long elderId, @Valid @RequestBody Request req) {
        familyAccessService.requireElderAccess(elderId);
        long uid = familyAccessService.currentUserId();
        Reminder r = new Reminder();
        apply(r, req);
        r.setElderId(elderId);
        r.setCreatedByUserId(uid);
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        reminderMapper.insert(r);
        return ApiResponse.ok(r);
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long elderId, @PathVariable Long id, @Valid @RequestBody Request req) {
        familyAccessService.requireElderAccess(elderId);
        Reminder r = requireReminder(elderId, id);
        apply(r, req);
        r.setUpdatedAt(LocalDateTime.now());
        reminderMapper.updateById(r);
        return ApiResponse.ok(r);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long elderId, @PathVariable Long id) {
        familyAccessService.requireElderAccess(elderId);
        Reminder r = requireReminder(elderId, id);
        reminderMapper.deleteById(r.getId());
        return ApiResponse.ok();
    }

    private Reminder requireReminder(Long elderId, Long id) {
        Reminder r = reminderMapper.selectById(id);
        if (r == null || !elderId.equals(r.getElderId())) throw new IllegalArgumentException("提醒不存在");
        return r;
    }

    private void apply(Reminder r, Request req) {
        r.setTitle(req.title().trim());
        r.setType(req.type().trim());
        r.setScheduleTime(req.scheduleTime().trim());
        r.setDosage(req.dosage() == null || req.dosage().isBlank() ? null : req.dosage().trim());
        r.setRepeatRule(req.repeatRule() == null || req.repeatRule().isBlank() ? null : req.repeatRule().trim());
        r.setNotifyAfterMinutes(req.notifyAfterMinutes());
        r.setEnabled(req.enabled());
    }
}
