package com.roc.netty.app.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
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

    private boolean isWindows() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win");
    }

    @GetMapping("/top")
    public Map<String, Object> getTopInfo() {
        Map<String, Object> result = new HashMap<>();
        
        if (isWindows()) {
            // Windows implementation
            try {
                Process process = Runtime.getRuntime().exec("wmic cpu get loadpercentage /value");
                process.waitFor(2, TimeUnit.SECONDS);
                
                List<Map<String, String>> processes = new ArrayList<>();
                Map<String, String> systemInfo = new HashMap<>();
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("LoadPercentage")) {
                            String[] parts = line.split("=");
                            if (parts.length == 2) {
                                systemInfo.put("CPU Usage", parts[1].trim() + "%");
                            }
                        }
                    }
                }
                
                // Get memory info for Windows
                process = Runtime.getRuntime().exec("wmic OS get FreePhysicalMemory,TotalVisibleMemorySize /Value");
                process.waitFor(2, TimeUnit.SECONDS);
                
                long freeMemory = 0;
                long totalMemory = 0;
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("FreePhysicalMemory")) {
                            String[] parts = line.split("=");
                            if (parts.length == 2) {
                                freeMemory = Long.parseLong(parts[1].trim());
                            }
                        } else if (line.contains("TotalVisibleMemorySize")) {
                            String[] parts = line.split("=");
                            if (parts.length == 2) {
                                totalMemory = Long.parseLong(parts[1].trim());
                            }
                        }
                    }
                }
                
                if (totalMemory > 0) {
                    long usedMemory = totalMemory - freeMemory;
                    double usedPercentage = (usedMemory * 100.0) / totalMemory;
                    systemInfo.put("Memory Usage", String.format("%.1f%%", usedPercentage));
                    systemInfo.put("Total Memory", formatSize(totalMemory * 1024));
                    systemInfo.put("Used Memory", formatSize(usedMemory * 1024));
                    systemInfo.put("Free Memory", formatSize(freeMemory * 1024));
                }
                
                result.put("system", systemInfo);
                result.put("processes", processes);
                result.put("timestamp", System.currentTimeMillis());
                
            } catch (IOException | InterruptedException e) {
                log.error("获取Windows系统信息失败", e);
                result.put("error", e.getMessage());
            }
        } else {
            // Original Unix/Mac implementation
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
        }
        return result;
    }

    @GetMapping("/cpu")
    public Map<String, Object> getCpuInfo() {
        Map<String, Object> result = new HashMap<>();
        
        if (isWindows()) {
            // Windows implementation
            try {
                Process process = Runtime.getRuntime().exec("wmic cpu get loadpercentage /value");
                process.waitFor(2, TimeUnit.SECONDS);
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("LoadPercentage")) {
                            String[] parts = line.split("=");
                            if (parts.length == 2) {
                                double load = Double.parseDouble(parts[1].trim());
                                result.put("used", load);
                                result.put("idle", 100.0 - load);
                                result.put("system", 0);
                                result.put("user", load);
                                result.put("raw", "CPU Load: " + load + "%");
                            }
                        }
                    }
                }
                
                // Get CPU name and cores for additional info
                process = Runtime.getRuntime().exec("wmic cpu get name,numberofcores");
                process.waitFor(2, TimeUnit.SECONDS);
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    boolean firstLine = true;
                    while ((line = reader.readLine()) != null) {
                        if (!firstLine && !line.trim().isEmpty()) {
                            result.put("cpuInfo", line.trim());
                            break;
                        }
                        firstLine = false;
                    }
                }
                
            } catch (IOException | InterruptedException e) {
                log.error("获取Windows CPU信息失败", e);
                result.put("error", e.getMessage());
            }
        } else {
            // Original Unix/Mac implementation
            try {
                String[] cmd = {"/bin/sh", "-c", "top -l 1 -n 0 | grep -E '^CPU'"};
                Process process = Runtime.getRuntime().exec(cmd);

                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroy();
                    throw new RuntimeException("Command timed out");
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null) {
                        result.put("raw", line);

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
        }
        return result;
    }

    @GetMapping("/memory")
    public Map<String, Object> getMemoryInfo() {
        Map<String, Object> result = new HashMap<>();
        
        if (isWindows()) {
            // Windows implementation
            try {
                Process process = Runtime.getRuntime().exec("wmic OS get FreePhysicalMemory,TotalVisibleMemorySize /Value");
                process.waitFor(2, TimeUnit.SECONDS);
                
                long freeMemory = 0;
                long totalMemory = 0;
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("FreePhysicalMemory")) {
                            String[] parts = line.split("=");
                            if (parts.length == 2) {
                                freeMemory = Long.parseLong(parts[1].trim());
                            }
                        } else if (line.contains("TotalVisibleMemorySize")) {
                            String[] parts = line.split("=");
                            if (parts.length == 2) {
                                totalMemory = Long.parseLong(parts[1].trim());
                            }
                        }
                    }
                }
                
                if (totalMemory > 0) {
                    long usedMemory = totalMemory - freeMemory;
                    double usedPercentage = (usedMemory * 100.0) / totalMemory;
                    
                    result.put("total", totalMemory / 1024.0);  // Convert KB to MB
                    result.put("used", usedMemory / 1024.0);     // Convert KB to MB
                    result.put("free", freeMemory / 1024.0);      // Convert KB to MB
                    result.put("usedPercentage", usedPercentage);
                    result.put("raw", String.format("Total: %.1f MB, Used: %.1f MB (%.1f%%), Free: %.1f MB", 
                            totalMemory/1024.0, usedMemory/1024.0, usedPercentage, freeMemory/1024.0));
                } else {
                    result.put("error", "无法获取内存信息");
                }
                
                // Get page file info
                process = Runtime.getRuntime().exec("wmic pagefile get AllocatedBaseSize,CurrentUsage /Value");
                process.waitFor(2, TimeUnit.SECONDS);
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    long pageFileTotal = 0;
                    long pageFileUsed = 0;
                    
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("AllocatedBaseSize")) {
                            String[] parts = line.split("=");
                            if (parts.length == 2) {
                                pageFileTotal = Long.parseLong(parts[1].trim());
                            }
                        } else if (line.contains("CurrentUsage")) {
                            String[] parts = line.split("=");
                            if (parts.length == 2) {
                                pageFileUsed = Long.parseLong(parts[1].trim());
                            }
                        }
                    }
                    
                    if (pageFileTotal > 0) {
                        Map<String, Object> pageFile = new HashMap<>();
                        pageFile.put("total", pageFileTotal);
                        pageFile.put("used", pageFileUsed);
                        pageFile.put("free", pageFileTotal - pageFileUsed);
                        pageFile.put("usedPercentage", (pageFileUsed * 100.0) / pageFileTotal);
                        result.put("pageFile", pageFile);
                    }
                }
                
            } catch (IOException | InterruptedException e) {
                log.error("获取Windows内存信息失败", e);
                result.put("error", e.getMessage());
            }
        } else {
            // Original Unix/Mac implementation
            try {
                String[] cmd = {"/bin/sh", "-c", "top -l 1 -n 0 | grep -E '^Phys'"};
                Process process = Runtime.getRuntime().exec(cmd);
                
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
        }
        return result;
    }

    @GetMapping("/jvm")
    public Map<String, Object> getJvmInfo() {
        Map<String, Object> result = new HashMap<>();

        // 获取运行时信息
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        // 内存使用情况
        Map<String, Object> memoryInfo = new HashMap<>();
        memoryInfo.put("max", formatSize(maxMemory));
        memoryInfo.put("total", formatSize(totalMemory));
        memoryInfo.put("free", formatSize(freeMemory));
        memoryInfo.put("used", formatSize(usedMemory));
        memoryInfo.put("usage", String.format("%.2f%%", (usedMemory * 100.0) / totalMemory));

        // 获取CPU使用率
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        double systemLoad = osBean.getSystemLoadAverage();
        int availableProcessors = osBean.getAvailableProcessors();

        // 获取JVM进程CPU使用率 (需要com.sun.management.OperatingSystemMXBean)
        double processCpuLoad = -1;
        try {
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                processCpuLoad = ((com.sun.management.OperatingSystemMXBean) osBean).getProcessCpuLoad() * 100;
            }
        } catch (Exception e) {
            log.warn("Failed to get process CPU load: {}", e.getMessage());
        }

        // 获取线程信息
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int threadCount = threadBean.getThreadCount();
        int peakThreadCount = threadBean.getPeakThreadCount();

        // 获取JVM启动时间和运行时间
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        long uptime = runtimeBean.getUptime();

        result.put("memory", memoryInfo);
        Map<String, Object> cpuMap = new HashMap<>();
        cpuMap.put("systemLoad", systemLoad);
        cpuMap.put("processCpuLoad", processCpuLoad >= 0 ? String.format("%.2f%%", processCpuLoad) : "N/A");
        cpuMap.put("availableProcessors", availableProcessors);
        result.put("cpu", cpuMap);

        Map<String, Object> threadsMap = new HashMap<>();
        threadsMap.put("count", threadCount);
        threadsMap.put("peak", peakThreadCount);
        threadsMap.put("uptime", formatUptime(uptime));
        threadsMap.put("timestamp", System.currentTimeMillis());
        result.put("threads", threadsMap);

        return result;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private String formatUptime(long millis) {
        long seconds = millis / 1000;
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0 || sb.length() > 0) sb.append(hours).append("h ");
        if (minutes > 0 || sb.length() > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");
        return sb.toString();
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
