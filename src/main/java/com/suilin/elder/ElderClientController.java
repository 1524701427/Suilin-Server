package com.suilin.elder;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.suilin.common.ApiResponse;
import com.suilin.elder.entity.Elder;
import com.suilin.elder.mapper.ElderMapper;
import com.suilin.reminder.entity.Reminder;
import com.suilin.reminder.entity.ReminderRecord;
import com.suilin.reminder.mapper.ReminderMapper;
import com.suilin.reminder.mapper.ReminderRecordMapper;
import com.suilin.sos.entity.SosEvent;
import com.suilin.sos.mapper.SosEventMapper;
import com.suilin.user.entity.User;
import com.suilin.user.mapper.UserMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/elder-client")
@RequiredArgsConstructor
public class ElderClientController {
    private final ElderMapper elderMapper;
    private final ReminderMapper reminderMapper;
    private final ReminderRecordMapper reminderRecordMapper;
    private final SosEventMapper sosEventMapper;
    private final UserMapper userMapper;

    public record ClientRequest(@NotBlank String clientToken) {}
    public record SosRequest(@NotBlank String clientToken, BigDecimal latitude, BigDecimal longitude) {}

    private Elder boundElder(String clientToken) {
        Elder elder = elderMapper.selectOne(new LambdaQueryWrapper<Elder>()
                .eq(Elder::getBoundClientToken, clientToken)
                .eq(Elder::getBindStatus, "BOUND"));
        if (elder == null) throw new IllegalArgumentException("长辈端绑定凭证无效");
        return elder;
    }

    @GetMapping("/profile")
    public ApiResponse<?> profile(@RequestParam String clientToken) {
        Elder elder = boundElder(clientToken);
        User creator = userMapper.selectById(elder.getCreatorUserId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", elder.getId());
        data.put("name", elder.getName());
        data.put("relation", elder.getRelation());
        data.put("birthday", elder.getBirthday());
        data.put("age", Period.between(elder.getBirthday(), LocalDate.now()).getYears());
        Map<String, Object> contact = new LinkedHashMap<>();
        if (creator != null) {
            contact.put("name", creator.getName());
            contact.put("phone", creator.getPhone());
        }
        data.put("familyContact", contact);
        return ApiResponse.ok(data);
    }

    @GetMapping("/reminders")
    public ApiResponse<?> reminders(@RequestParam String clientToken) {
        Elder elder = boundElder(clientToken);
        List<Reminder> list = reminderMapper.selectList(new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getElderId, elder.getId())
                .eq(Reminder::getEnabled, true)
                .orderByAsc(Reminder::getScheduleTime));
        return ApiResponse.ok(list);
    }

    @PostMapping("/reminders/{reminderId}/complete")
    public ApiResponse<?> complete(@PathVariable Long reminderId, @Valid @RequestBody ClientRequest req) {
        Elder elder = boundElder(req.clientToken());
        Reminder reminder = reminderMapper.selectById(reminderId);
        if (reminder == null || !elder.getId().equals(reminder.getElderId())) {
            throw new IllegalArgumentException("提醒不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        ReminderRecord record = new ReminderRecord();
        record.setReminderId(reminderId);
        record.setElderId(elder.getId());
        record.setScheduledAt(now);
        record.setStatus("COMPLETED");
        record.setCompletedAt(now);
        record.setSourceType("ELDER_ACTION");
        record.setCreatedAt(now);
        reminderRecordMapper.insert(record);
        return ApiResponse.ok(Map.of("recordId", record.getId(), "status", record.getStatus(), "completedAt", record.getCompletedAt()));
    }

    @PostMapping("/sos")
    public ApiResponse<?> sos(@Valid @RequestBody SosRequest req) {
        Elder elder = boundElder(req.clientToken());
        SosEvent event = new SosEvent();
        event.setElderId(elder.getId());
        event.setSourceType("ELDER_ACTION");
        event.setSourceRef(elder.getBoundClientId());
        event.setLatitude(req.latitude());
        event.setLongitude(req.longitude());
        event.setStatus("OPEN");
        event.setCreatedAt(LocalDateTime.now());
        sosEventMapper.insert(event);
        return ApiResponse.ok(Map.of("eventId", event.getId(), "status", event.getStatus()));
    }
}
