package com.roc.netty.client.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AppBootCtrlService {
    // 存储进程的映射表 <jar文件名, 进程>
    private final ConcurrentMap<String, Process> runningProcesses = new ConcurrentHashMap<>();

    /**
     * 启动JAR程序
     *
     * @param jarPath JAR文件路径
     * @param args    启动参数
     * @return 是否启动成功
     */
    public boolean startJar(String jarPath, List<String> args) {
        try {
            // 检查JAR文件是否存在
            File jarFile = new File(jarPath);
            if (!jarFile.exists() || !jarFile.isFile() || !jarFile.getName().endsWith(".jar")) {
                log.error("Invalid JAR file: {}", jarPath);
                return false;
            }

            // 如果已经有一个实例在运行，先停止它
            if (runningProcesses.containsKey(jarFile.getName())) {
                stopJar(jarFile.getName());
            }

            // 构建命令
            List<String> command = new ArrayList<>();
            command.add("java");
            command.add("-jar");
            command.add(jarPath);
            if (args != null) {
                command.addAll(args);
            }

            // 启动进程
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(jarFile.getParentFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            runningProcesses.put(jarFile.getName(), process);

            // 启动一个线程来读取进程输出
            startOutputReader(process, jarFile.getName());

            log.info("Started JAR: {}", jarPath);
            return true;
        } catch (Exception e) {
            log.error("Failed to start JAR: " + jarPath, e);
            return false;
        }
    }

    /**
     * 停止JAR程序
     *
     * @param jarName JAR文件名
     * @return 是否停止成功
     */
    public boolean stopJar(String jarName) {
        Process process = runningProcesses.get(jarName);
        if (process == null) {
            log.warn("No running process found for JAR: {}", jarName);
            return false;
        }

        try {
            // 先尝试正常关闭
            process.destroy();

            // 等待进程结束，最多等待5秒
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                // 强制终止
                process.destroyForcibly();
                log.warn("Forcefully terminated JAR: {}", jarName);
            }

            runningProcesses.remove(jarName);
            log.info("Stopped JAR: {}", jarName);
            return true;
        } catch (Exception e) {
            log.error("Error stopping JAR: {}", jarName, e);
            return false;
        }
    }

    /**
     * 检查JAR程序是否在运行
     *
     * @param jarName JAR文件名
     * @return 是否在运行
     */
    public boolean isJarRunning(String jarName) {
        Process process = runningProcesses.get(jarName);
        if (process == null) {
            return false;
        }

        try {
            // 检查进程是否还在运行
            int exitValue = process.exitValue();
            // 如果执行到这里，说明进程已经结束
            runningProcesses.remove(jarName);
            return false;
        } catch (IllegalThreadStateException e) {
            // 进程仍在运行
            return true;
        }
    }

    /**
     * 获取所有正在运行的JAR程序
     *
     * @return 正在运行的JAR程序列表
     */
    public List<String> getRunningJars() {
        // 清除已经结束的进程
        runningProcesses.entrySet().removeIf(entry -> {
            try {
                entry.getValue().exitValue();
                return true; // 如果exitValue()没有抛出异常，说明进程已结束
            } catch (IllegalThreadStateException e) {
                return false; // 进程仍在运行
            }
        });

        return new ArrayList<>(runningProcesses.keySet());
    }

    /**
     * 启动输出读取线程
     */
    private void startOutputReader(Process process, String jarName) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[{}] {}", jarName, line);
                }
            } catch (IOException e) {
                log.error("Error reading output from " + jarName, e);
            } finally {
                // 进程结束时从运行列表中移除
                runningProcesses.remove(jarName);
                log.info("Process terminated: {}", jarName);
            }
        }, "output-reader-" + jarName).start();
    }
}