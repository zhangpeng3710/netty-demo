package com.roc.netty.client.service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

public interface LogFileService {

    /**
     * Upload log files within a date range
     */
    Map<String, Object> uploadLogFilesByDateRange(LocalDate startDate, LocalDate endDate);

    /**
     * Upload a specific log file by path
     */
    Map<String, Object> uploadLogFileByPath(String relativePath);

    /**
     * List all available log files
     */
    Map<String, Object> listLogFiles();

    /**
     * Send a file to the server
     */
    boolean sendFileToServer(Path filePath);
}
