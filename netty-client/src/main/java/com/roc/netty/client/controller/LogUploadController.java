package com.roc.netty.client.controller;

import com.roc.netty.client.service.LogFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;


/**
 * @Description Controller for handling log file uploads
 * @Author: Zhang Peng
 * @Date: 2025/5/27
 */
@Slf4j
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogUploadController {

    private final LogFileService logFileService;

    @PostMapping("/uploadByTimePoint")
    public ResponseEntity<Map<String, Object>> uploadLogFileByTimePoint(
            @RequestParam String startDay,
            @RequestParam String endDay) {

        try {
            LocalDate startDate = LocalDate.parse(startDay);
            LocalDate endDate = LocalDate.parse(endDay);

            // Validate date range
            if (startDate.isAfter(endDate)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Start date must be before or equal to end date");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            Map<String, Object> response = logFileService.uploadLogFilesByDateRange(startDate, endDate);
            return ResponseEntity.ok(response);

        } catch (DateTimeParseException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Invalid date format. Please use yyyy-MM-dd format");
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/uploadByPath")
    public ResponseEntity<Map<String, Object>> uploadLogFileByPath(@RequestParam("filePath") String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "File path is required");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        Map<String, Object> response = logFileService.uploadLogFileByPath(filePath);
        boolean success = (boolean) response.getOrDefault("success", false);

        if (success) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listUploadedLogs() {
        Map<String, Object> response = logFileService.listLogFiles();
        boolean success = (boolean) response.getOrDefault("success", false);

        if (success) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(500).body(response);
        }
    }
}
