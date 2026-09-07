package com.suilin.common;

import cn.dev33.satoken.exception.NotLoginException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> illegal(IllegalArgumentException e) { return ApiResponse.fail(400, e.getMessage()); }

    @ExceptionHandler(NotLoginException.class)
    public ApiResponse<Void> notLogin(NotLoginException e) { return ApiResponse.fail(401, "请先登录"); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> validation(MethodArgumentNotValidException e) {
        var error = e.getBindingResult().getFieldError();
        return ApiResponse.fail(400, error == null ? "参数错误" : error.getDefaultMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> other(Exception e) { return ApiResponse.fail(500, "服务器内部错误"); }
}
