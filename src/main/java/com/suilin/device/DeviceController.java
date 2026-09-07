package com.suilin.device;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.suilin.common.ApiResponse;
import com.suilin.device.entity.Device;
import com.suilin.device.mapper.DeviceMapper;
import com.suilin.elder.entity.Elder;
import com.suilin.elder.mapper.ElderMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/elders/{elderId}/devices")
@RequiredArgsConstructor
public class DeviceController {
    private final DeviceMapper deviceMapper;
    private final ElderMapper elderMapper;

    public record CreateRequest(@NotBlank String deviceType, @NotBlank String deviceSn) {}

    private void assertOwner(Long elderId) {
        long uid = StpUtil.getLoginIdAsLong();
        Elder elder = elderMapper.selectById(elderId);
        if (elder == null || !Long.valueOf(uid).equals(elder.getCreatorUserId())) {
            throw new IllegalArgumentException("无权访问该长辈");
        }
    }

    @GetMapping
    public ApiResponse<?> list(@PathVariable Long elderId) {
        assertOwner(elderId);
        return ApiResponse.ok(deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                .eq(Device::getElderId, elderId)
                .orderByDesc(Device::getCreatedAt)));
    }

    @PostMapping
    public ApiResponse<?> create(@PathVariable Long elderId, @Valid @RequestBody CreateRequest req) {
        assertOwner(elderId);
        if (deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getDeviceSn, req.deviceSn().trim())) > 0) {
            throw new IllegalArgumentException("设备码已被绑定");
        }
        Device device = new Device();
        device.setElderId(elderId);
        device.setDeviceType(req.deviceType().trim());
        device.setDeviceSn(req.deviceSn().trim());
        device.setStatus("OFFLINE");
        device.setCreatedAt(LocalDateTime.now());
        deviceMapper.insert(device);
        return ApiResponse.ok(device);
    }
}
