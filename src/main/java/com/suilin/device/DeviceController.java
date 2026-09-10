package com.suilin.device;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.suilin.common.ApiResponse;
import com.suilin.device.entity.Device;
import com.suilin.device.mapper.DeviceMapper;
import com.suilin.family.FamilyAccessService;
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
    private final FamilyAccessService familyAccessService;

    public record CreateRequest(@NotBlank String deviceType, @NotBlank String deviceSn) {}

    @GetMapping
    public ApiResponse<?> list(@PathVariable Long elderId) {
        familyAccessService.requireElderAccess(elderId);
        return ApiResponse.ok(deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                .eq(Device::getElderId, elderId)
                .orderByDesc(Device::getCreatedAt)));
    }

    @PostMapping
    public ApiResponse<?> create(@PathVariable Long elderId, @Valid @RequestBody CreateRequest req) {
        familyAccessService.requireElderAccess(elderId);
        String sn = req.deviceSn().trim();
        if (deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getDeviceSn, sn)) > 0) {
            throw new IllegalArgumentException("设备码已被绑定");
        }
        Device device = new Device();
        device.setElderId(elderId);
        device.setDeviceType(req.deviceType().trim());
        device.setDeviceSn(sn);
        device.setStatus("OFFLINE");
        device.setCreatedAt(LocalDateTime.now());
        deviceMapper.insert(device);
        return ApiResponse.ok(device);
    }

    @DeleteMapping("/{deviceId}")
    public ApiResponse<Void> unbind(@PathVariable Long elderId, @PathVariable Long deviceId) {
        familyAccessService.requireElderAccess(elderId);
        Device device = deviceMapper.selectById(deviceId);
        if (device == null || !elderId.equals(device.getElderId())) throw new IllegalArgumentException("设备不存在");
        deviceMapper.deleteById(deviceId);
        return ApiResponse.ok();
    }
}
