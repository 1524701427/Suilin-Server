package com.suilin.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.suilin.common.ApiResponse;
import com.suilin.family.FamilyAccessService;
import com.suilin.family.entity.Family;
import com.suilin.family.entity.FamilyMember;
import com.suilin.family.mapper.FamilyMemberMapper;
import com.suilin.task.entity.CareTask;
import com.suilin.task.mapper.CareTaskMapper;
import com.suilin.user.entity.User;
import com.suilin.user.mapper.UserMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/care-tasks")
@RequiredArgsConstructor
public class CareTaskController {
    private final CareTaskMapper taskMapper;
    private final FamilyAccessService familyAccessService;
    private final FamilyMemberMapper memberMapper;
    private final UserMapper userMapper;

    public record Request(@NotBlank String title, Long elderId, Long assigneeUserId, LocalDateTime dueAt, String note, String status) {}

    @GetMapping
    public ApiResponse<?> list() {
        Family family = familyAccessService.currentFamily();
        return ApiResponse.ok(taskMapper.selectList(new LambdaQueryWrapper<CareTask>()
                .eq(CareTask::getFamilyId, family.getId())
                .orderByAsc(CareTask::getStatus)
                .orderByAsc(CareTask::getDueAt)
                .orderByDesc(CareTask::getCreatedAt))
                .stream().map(this::view).toList());
    }

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody Request req) {
        Family family = familyAccessService.currentFamily();
        long uid = familyAccessService.currentUserId();
        validateAssignee(family.getId(), req.assigneeUserId());
        if (req.elderId() != null) familyAccessService.requireElderAccess(req.elderId());
        CareTask task = new CareTask();
        task.setFamilyId(family.getId());
        task.setElderId(req.elderId());
        task.setTitle(req.title().trim());
        task.setAssigneeUserId(req.assigneeUserId());
        task.setDueAt(req.dueAt());
        task.setNote(blankToNull(req.note()));
        task.setStatus(validStatus(req.status()));
        task.setCreatedByUserId(uid);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        return ApiResponse.ok(view(task));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable Long id, @Valid @RequestBody Request req) {
        Family family = familyAccessService.currentFamily();
        CareTask task = requireTask(id, family.getId());
        validateAssignee(family.getId(), req.assigneeUserId());
        if (req.elderId() != null) familyAccessService.requireElderAccess(req.elderId());
        task.setTitle(req.title().trim());
        task.setElderId(req.elderId());
        task.setAssigneeUserId(req.assigneeUserId());
        task.setDueAt(req.dueAt());
        task.setNote(blankToNull(req.note()));
        task.setStatus(validStatus(req.status()));
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return ApiResponse.ok(view(task));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<?> complete(@PathVariable Long id) {
        Family family = familyAccessService.currentFamily();
        CareTask task = requireTask(id, family.getId());
        task.setStatus("DONE");
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return ApiResponse.ok(view(task));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Family family = familyAccessService.currentFamily();
        CareTask task = requireTask(id, family.getId());
        taskMapper.deleteById(task.getId());
        return ApiResponse.ok();
    }

    private CareTask requireTask(Long id, Long familyId) {
        CareTask task = taskMapper.selectById(id);
        if (task == null || !familyId.equals(task.getFamilyId())) throw new IllegalArgumentException("照护任务不存在");
        return task;
    }

    private void validateAssignee(Long familyId, Long userId) {
        if (userId == null) return;
        FamilyMember member = memberMapper.selectOne(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getFamilyId, familyId).eq(FamilyMember::getUserId, userId).eq(FamilyMember::getStatus, "ACTIVE").last("limit 1"));
        if (member == null) throw new IllegalArgumentException("负责人必须是当前家庭成员");
    }

    private String validStatus(String status) {
        String value = status == null || status.isBlank() ? "TODO" : status;
        if (!("TODO".equals(value) || "DOING".equals(value) || "DONE".equals(value))) throw new IllegalArgumentException("无效的任务状态");
        return value;
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private Map<String,Object> view(CareTask task) {
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("id", task.getId());
        data.put("elderId", task.getElderId());
        data.put("title", task.getTitle());
        data.put("assigneeUserId", task.getAssigneeUserId());
        User assignee = task.getAssigneeUserId() == null ? null : userMapper.selectById(task.getAssigneeUserId());
        data.put("assigneeName", assignee == null ? "" : assignee.getName());
        data.put("dueAt", task.getDueAt());
        data.put("note", task.getNote());
        data.put("status", task.getStatus());
        data.put("createdByUserId", task.getCreatedByUserId());
        return data;
    }
}
