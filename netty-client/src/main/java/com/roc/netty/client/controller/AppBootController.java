package com.roc.netty.client.controller;

import com.roc.netty.client.dto.ApiResponse;
import com.roc.netty.client.service.AppBootCtrlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/app")
@RequiredArgsConstructor
public class AppBootController {
    private final AppBootCtrlService appBootCtrlService;

    @PostMapping("/start")
    public ApiResponse<String> startApp(
            @RequestParam String jarPath,
            @RequestParam(required = false) List<String> args) {
        log.info("Starting JAR: {} with args: {}", jarPath, args);
        boolean success = appBootCtrlService.startJar(jarPath, args);
        if (success) {
            return ApiResponse.success("Application started successfully");
        } else {
            return ApiResponse.error(500, "Failed to start application");
        }
    }

    @PostMapping("/stop")
    public ApiResponse<String> stopApp(@RequestParam String jarName) {
        log.info("Stopping JAR: {}", jarName);
        boolean success = appBootCtrlService.stopJar(jarName);
        if (success) {
            return ApiResponse.success("Application stopped successfully");
        } else {
            return ApiResponse.error(500, "Failed to stop application");
        }
    }

    @GetMapping("/status")
    public ApiResponse<Boolean> getAppStatus(@RequestParam String jarName) {
        boolean isRunning = appBootCtrlService.isJarRunning(jarName);
        return ApiResponse.success("Status retrieved successfully", isRunning);
    }

    @GetMapping("/list")
    public ApiResponse<List<String>> listRunningApps() {
        return ApiResponse.success("Running applications retrieved successfully",
                appBootCtrlService.getRunningJars());
    }
}