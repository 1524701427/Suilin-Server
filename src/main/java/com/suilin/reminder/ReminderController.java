package com.suilin.reminder;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.suilin.common.ApiResponse;
import com.suilin.elder.entity.Elder;
import com.suilin.elder.mapper.ElderMapper;
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
    private final ElderMapper elderMapper;

    public record Request(@NotBlank String title, @NotBlank String type, @NotBlank String scheduleTime,
                          String dosage, String repeatRule, Integer notifyAfterMinutes, @NotNull Boolean enabled) {}

    private void assertOwner(Long elderId, long userId) {
        Elder elder = elderMapper.selectById(elderId);
        if (elder == null || !Long.valueOf(userId).equals(elder.getCreatorUserId())) throw new IllegalArgumentException("无权访问该长辈");
    }

    @GetMapping
    public ApiResponse<?> list(@PathVariable Long elderId) {
        long uid = StpUtil.getLoginIdAsLong(); assertOwner(elderId, uid);
        return ApiResponse.ok(reminderMapper.selectList(new LambdaQueryWrapper<Reminder>().eq(Reminder::getElderId, elderId).orderByAsc(Reminder::getScheduleTime)));
    }

    @PostMapping
    public ApiResponse<?> create(@PathVariable Long elderId, @Valid @RequestBody Request req) {
        long uid = StpUtil.getLoginIdAsLong(); assertOwner(elderId, uid);
        Reminder r = new Reminder();
        r.setElderId(elderId); r.setCreatedByUserId(uid); r.setTitle(req.title()); r.setType(req.type()); r.setScheduleTime(req.scheduleTime());
        r.setDosage(req.dosage()); r.setRepeatRule(req.repeatRule()); r.setNotifyAfterMinutes(req.notifyAfterMinutes()); r.setEnabled(req.enabled());
        r.setCreatedAt(LocalDateTime.now()); r.setUpdatedAt(LocalDateTime.now()); reminderMapper.insert(r);
        return ApiResponse.ok(r);
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long elderId, @PathVariable Long id, @Valid @RequestBody Request req) {
        long uid = StpUtil.getLoginIdAsLong(); assertOwner(elderId, uid);
        Reminder r = reminderMapper.selectById(id);
        if (r == null || !elderId.equals(r.getElderId())) throw new IllegalArgumentException("提醒不存在");
        r.setTitle(req.title()); r.setType(req.type()); r.setScheduleTime(req.scheduleTime()); r.setDosage(req.dosage());
        r.setRepeatRule(req.repeatRule()); r.setNotifyAfterMinutes(req.notifyAfterMinutes()); r.setEnabled(req.enabled()); r.setUpdatedAt(LocalDateTime.now());
        reminderMapper.updateById(r); return ApiResponse.ok(r);
    }
}
