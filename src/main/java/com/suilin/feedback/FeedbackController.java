package com.suilin.feedback;

import cn.dev33.satoken.stp.StpUtil;
import com.suilin.common.ApiResponse;
import com.suilin.feedback.entity.Feedback;
import com.suilin.feedback.mapper.FeedbackMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {
    private final FeedbackMapper mapper;

    public record Request(@NotBlank @Size(max=1000) String content, @Size(max=100) String contact) {}

    @PostMapping
    public ApiResponse<?> create(@Valid @RequestBody Request req) {
        Feedback feedback = new Feedback();
        feedback.setUserId(StpUtil.getLoginIdAsLong());
        feedback.setContent(req.content().trim());
        feedback.setContact(req.contact() == null || req.contact().isBlank() ? null : req.contact().trim());
        feedback.setStatus("NEW");
        feedback.setCreatedAt(LocalDateTime.now());
        mapper.insert(feedback);
        return ApiResponse.ok(feedback);
    }
}
