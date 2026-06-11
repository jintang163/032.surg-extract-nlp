package com.surg.extract.controller;

import com.surg.extract.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户登录、登出接口")
public class AuthController {

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户名密码登录")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> loginData) {
        String username = loginData.get("username");
        String password = loginData.get("password");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", "mock-jwt-token-" + System.currentTimeMillis());
        result.put("userId", 1L);
        result.put("username", username);
        result.put("realName", "张医生");
        result.put("role", "DOCTOR");
        result.put("department", "普外科");
        result.put("title", "主任医师");

        if ("admin".equals(username)) {
            result.put("role", "ADMIN");
            result.put("realName", "系统管理员");
            result.put("department", "信息科");
            result.put("title", "高级工程师");
        }

        return Result.success("登录成功", result);
    }

    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "用户退出登录")
    public Result<Void> logout() {
        return Result.success("退出成功");
    }

    @GetMapping("/user-info")
    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户信息")
    public Result<Map<String, Object>> getUserInfo() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", 1L);
        result.put("username", "zhangyi");
        result.put("realName", "张医生");
        result.put("role", "DOCTOR");
        result.put("department", "普外科");
        result.put("title", "主任医师");
        result.put("phone", "13800000001");
        return Result.success(result);
    }
}
