package com.roc.netty.app.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Description
 * @Author: Zhang Peng
 * @Date: 2025/5/26
 */
@Slf4j
@RestController
public class TopMonitorController {
    private static final String TOP_COMMAND = "top -l 1 -n 0 | grep -E '^Processes|^\\d+'";
    private static final Pattern PROCESS_PATTERN = Pattern.compile(
            "^(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(.+)"
    );

    @GetMapping("/top")
    public Map<String, Object> getTopInfo() {
        Map<String, Object> result = new HashMap<>();
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", TOP_COMMAND});
            process.waitFor(2, TimeUnit.SECONDS);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                List<Map<String, String>> processes = new ArrayList<>();
                Map<String, String> systemInfo = new HashMap<>();

                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("Processes:")) {
                        // 解析系统级信息
                        String[] parts = line.split(";\\s*");
                        for (String part : parts) {
                            String[] keyValue = part.split(":\\s*", 2);
                            if (keyValue.length == 2) {
                                systemInfo.put(keyValue[0].trim(), keyValue[1].trim());
                            }
                        }
                    } else {
                        // 解析进程信息
                        Matcher matcher = PROCESS_PATTERN.matcher(line);
                        if (matcher.matches()) {
                            Map<String, String> processInfo = new HashMap<>();
                            processInfo.put("pid", matcher.group(1));
                            processInfo.put("command", matcher.group(11));
                            processInfo.put("%CPU", matcher.group(3));
                            processInfo.put("%MEM", matcher.group(8));
                            processes.add(processInfo);
                        }
                    }
                }

                result.put("system", systemInfo);
                result.put("processes", processes);
                result.put("timestamp", System.currentTimeMillis());
            }
        } catch (IOException | InterruptedException e) {
            log.error("获取top信息失败", e);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @GetMapping("/cpu")
    public Map<String, Object> getCpuInfo() {
        Map<String, Object> result = new HashMap<>();
        try {
            // 使用String[]方式执行shell命令，并处理错误流
            String[] cmd = {"/bin/sh", "-c", "top -l 1 -n 0 | grep -E '^CPU'"};

            // 创建进程
            Process process = Runtime.getRuntime().exec(cmd);

            // 设置超时
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroy();
                throw new RuntimeException("Command timed out");
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    // 示例: CPU usage: 10.0% user, 20.0% sys, 70.0% idle
                    result.put("raw", line);

                    // 解析CPU使用率
                    Pattern pattern = Pattern.compile(
                            "CPU usage:\\s*(\\d+\\.\\d+)% user,\\s*(\\d+\\.\\d+)% sys,\\s*(\\d+\\.\\d+)% idle"
                    );
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        result.put("user", Double.parseDouble(matcher.group(1)));
                        result.put("system", Double.parseDouble(matcher.group(2)));
                        result.put("idle", Double.parseDouble(matcher.group(3)));
                        result.put("used", 100.0 - Double.parseDouble(matcher.group(3)));
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            log.error("获取CPU信息失败", e);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @GetMapping("/memory")
    public Map<String, Object> getMemoryInfo() {
        Map<String, Object> result = new HashMap<>();
        try {
            // 使用String[]方式执行shell命令，并处理错误流
            String[] cmd = {"/bin/sh", "-c", "top -l 1 -n 0 | grep -E '^Phys'"};

            Process process = Runtime.getRuntime().exec(cmd);
            // 设置超时
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroy();
                throw new RuntimeException("Command timed out");
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    // 示例: PhysMem: 10G used (5G wired), 4G unused.
                    result.put("raw", line);

                    // 解析内存使用情况
                    Pattern pattern = Pattern.compile(
                            "PhysMem:\\s*(\\d+[KMG]) used \\((\\d+[KMG]) wired\\),\\s*(\\d+[KMG]) unused"
                    );
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        result.put("used", parseMemorySize(matcher.group(1)));
                        result.put("wired", parseMemorySize(matcher.group(2)));
                        result.put("unused", parseMemorySize(matcher.group(3)));

                        double total = parseMemorySize(matcher.group(1)) + parseMemorySize(matcher.group(3));
                        result.put("total", total);
                        result.put("usedPercentage", (parseMemorySize(matcher.group(1)) / total) * 100);
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            log.error("获取内存信息失败", e);
            result.put("error", e.getMessage());
        }
        return result;
    }

    private double parseMemorySize(String size) {
        if (size.endsWith("K")) {
            return Double.parseDouble(size.substring(0, size.length() - 1)) / 1024.0 / 1024.0;
        } else if (size.endsWith("M")) {
            return Double.parseDouble(size.substring(0, size.length() - 1)) / 1024.0;
        } else if (size.endsWith("G")) {
            return Double.parseDouble(size.substring(0, size.length() - 1));
        } else {
            return Double.parseDouble(size) / 1024.0 / 1024.0 / 1024.0;
        }
    }

}
