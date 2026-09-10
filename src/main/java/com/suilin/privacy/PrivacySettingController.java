package com.suilin.privacy;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.suilin.common.ApiResponse;
import com.suilin.privacy.entity.PrivacySetting;
import com.suilin.privacy.mapper.PrivacySettingMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/privacy-settings")
@RequiredArgsConstructor
public class PrivacySettingController {
    private final PrivacySettingMapper mapper;

    public record Request(@NotNull Boolean healthVisible,
                          @NotNull Boolean locationVisible,
                          @NotNull Boolean deviceVisible) {}

    @GetMapping
    public ApiResponse<?> get() {
        long uid = StpUtil.getLoginIdAsLong();
        PrivacySetting setting = mapper.selectOne(new LambdaQueryWrapper<PrivacySetting>().eq(PrivacySetting::getUserId, uid));
        if (setting == null) {
            setting = defaults(uid);
            mapper.insert(setting);
        }
        return ApiResponse.ok(setting);
    }

    @PutMapping
    public ApiResponse<?> update(@Valid @RequestBody Request req) {
        long uid = StpUtil.getLoginIdAsLong();
        PrivacySetting setting = mapper.selectOne(new LambdaQueryWrapper<PrivacySetting>().eq(PrivacySetting::getUserId, uid));
        if (setting == null) setting = defaults(uid);
        setting.setHealthVisible(req.healthVisible());
        setting.setLocationVisible(req.locationVisible());
        setting.setDeviceVisible(req.deviceVisible());
        setting.setUpdatedAt(LocalDateTime.now());
        if (setting.getId() == null) mapper.insert(setting); else mapper.updateById(setting);
        return ApiResponse.ok(setting);
    }

    private PrivacySetting defaults(long uid) {
        LocalDateTime now = LocalDateTime.now();
        PrivacySetting setting = new PrivacySetting();
        setting.setUserId(uid);
        setting.setHealthVisible(true);
        setting.setLocationVisible(false);
        setting.setDeviceVisible(true);
        setting.setCreatedAt(now);
        setting.setUpdatedAt(now);
        return setting;
    }
}
