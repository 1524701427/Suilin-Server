package com.suilin.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.suilin.common.ApiResponse;
import com.suilin.notification.entity.NotificationSetting;
import com.suilin.notification.mapper.NotificationSettingMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import cn.dev33.satoken.stp.StpUtil;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/notification-settings")
@RequiredArgsConstructor
public class NotificationSettingController {
    private final NotificationSettingMapper mapper;

    public record Request(@NotNull Boolean sosEnabled,
                          @NotNull Boolean reminderEnabled,
                          @NotNull Boolean healthEnabled,
                          @NotNull Boolean deviceEnabled,
                          @NotNull Boolean serviceEnabled) {}

    @GetMapping
    public ApiResponse<?> get() {
        long uid = StpUtil.getLoginIdAsLong();
        NotificationSetting setting = mapper.selectOne(new LambdaQueryWrapper<NotificationSetting>().eq(NotificationSetting::getUserId, uid));
        if (setting == null) {
            setting = defaults(uid);
            mapper.insert(setting);
        }
        return ApiResponse.ok(setting);
    }

    @PutMapping
    public ApiResponse<?> update(@Valid @RequestBody Request req) {
        long uid = StpUtil.getLoginIdAsLong();
        NotificationSetting setting = mapper.selectOne(new LambdaQueryWrapper<NotificationSetting>().eq(NotificationSetting::getUserId, uid));
        if (setting == null) setting = defaults(uid);
        setting.setSosEnabled(req.sosEnabled());
        setting.setReminderEnabled(req.reminderEnabled());
        setting.setHealthEnabled(req.healthEnabled());
        setting.setDeviceEnabled(req.deviceEnabled());
        setting.setServiceEnabled(req.serviceEnabled());
        setting.setUpdatedAt(LocalDateTime.now());
        if (setting.getId() == null) mapper.insert(setting); else mapper.updateById(setting);
        return ApiResponse.ok(setting);
    }

    private NotificationSetting defaults(long uid) {
        LocalDateTime now = LocalDateTime.now();
        NotificationSetting setting = new NotificationSetting();
        setting.setUserId(uid);
        setting.setSosEnabled(true);
        setting.setReminderEnabled(true);
        setting.setHealthEnabled(true);
        setting.setDeviceEnabled(true);
        setting.setServiceEnabled(false);
        setting.setCreatedAt(now);
        setting.setUpdatedAt(now);
        return setting;
    }
}
