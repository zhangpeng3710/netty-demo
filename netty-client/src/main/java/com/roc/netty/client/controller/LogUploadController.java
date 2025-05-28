package com.roc.netty.client.controller;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.util.OptionHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roc.netty.client.netty.NettyClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

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

    @Value("${logUpload.isCompressed}")
    private boolean beCompressed;

    @Value("${logUpload.bankAccount.isDesensitized}")
    private boolean beDesensitized;

    @Value("${logUpload.bankAccount.regex}")
    private String bankAccountRegex;

    private final NettyClient nettyClient;

    private final String logRootDirectory = System.getProperty("user.dir")
            + File.separator
            + OptionHelper.substVars("${LOG_PATH}", (LoggerContext) LoggerFactory.getILoggerFactory());


    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadLogFile(
            @RequestParam("filePath") String filePath) {
        Map<String, Object> response = new HashMap<>();

        // Validate file path
        if (filePath == null || filePath.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "File path is required");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            Path fileToUpload = Paths.get(logRootDirectory + File.separator + filePath);

            // Check if file exists and is readable
            if (!Files.exists(fileToUpload) || !Files.isReadable(fileToUpload)) {
                response.put("success", false);
                response.put("message", "File not found or not readable: " + filePath);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            // Get file info
            long fileSize = Files.size(fileToUpload);
            String fileName = fileToUpload.getFileName().toString();

            // Send file to Netty server
            boolean uploadSuccess = sendFileToServer(fileToUpload.toString());

            if (!uploadSuccess) {
                throw new IOException("Failed to send file to server");
            }

            // Prepare success response
            response.put("success", true);
            response.put("message", "Log file sent to server successfully");
            response.put("filename", fileName);
            response.put("size", fileSize);
            response.put("path", fileToUpload.toString());

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            response.put("success", false);
            response.put("message", "Failed to process file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Masks bank account numbers in the content (keeps first 4 and last 4 digits)
     */
    private String maskBankAccounts(String content) {
        // This regex matches 12-19 digit bank account numbers
        return content.replaceAll(bankAccountRegex, "$1****$3");
    }

    /**
     * Compresses data using GZIP
     */
    private byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(data.length);
        try (GZIPOutputStream gzipOS = new GZIPOutputStream(byteArrayOutputStream)) {
            gzipOS.write(data);
        }
        return byteArrayOutputStream.toByteArray();
    }

    /**
     * Sends a file to the Netty server with bank account masking and compression
     *
     * @param filePath Path to the file to send
     * @return true if the file was sent successfully, false otherwise
     */
    private boolean sendFileToServer(String filePath) {
        try {
            // Read file content as text for masking
            String fileContent = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
            String fileName = Paths.get(filePath).getFileName().toString();

            // Mask bank account numbers
            if (beDesensitized) {
                fileContent = maskBankAccounts(fileContent);
            }

            // Convert back to bytes for compression
            byte[] contentBytes = fileContent.getBytes(StandardCharsets.UTF_8);
            byte[] compressedContent;
            Map<String, Object> fileData = new HashMap<>();

            if (beCompressed) {
                // Compress the content
                compressedContent = compress(contentBytes);

                // Create a message with file info and content
                fileData.put("fileName", fileName);
                fileData.put("originalSize", contentBytes.length);
                fileData.put("compressedSize", compressedContent.length);
                fileData.put("content", Base64.getEncoder().encodeToString(compressedContent));
                fileData.put("isCompressed", true);
            } else {
                // No compression
                fileData.put("fileName", fileName);
                fileData.put("originalSize", contentBytes.length);
                fileData.put("content", fileContent);
                fileData.put("isCompressed", false);
            }
            fileData.put("fileName", fileName);
            fileData.put("originalSize", contentBytes.length);

            // Convert to JSON string
            String jsonData = new ObjectMapper().writeValueAsString(fileData);

            // Send to server
            return nettyClient.sendMessage(jsonData, true);

        } catch (IOException e) {
            log.error("Error processing or sending file to server: {}", e.getMessage(), e);
            return false;
        }
    }

    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listUploadedLogs() {
        Map<String, Object> response = new HashMap<>();
        Path uploadPath = Paths.get(logRootDirectory);

        if (!Files.exists(uploadPath) || !Files.isDirectory(uploadPath)) {
            response.put("success", false);
            response.put("message", "Upload directory does not exist");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        List<Map<String, Object>> fileList = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(uploadPath)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString().toLowerCase();
                        return fileName.endsWith(".log") || fileName.endsWith(".txt") || fileName.endsWith(".out");
                    })
                    .forEach(path -> {
                        try {
                            Map<String, Object> fileInfo = new HashMap<>();
                            fileInfo.put("name", path.getFileName().toString());
                            fileInfo.put("path", uploadPath.relativize(path).toString());
                            fileInfo.put("size", Files.size(path));
                            fileInfo.put("lastModified", Files.getLastModifiedTime(path).toMillis());
                            fileList.add(fileInfo);
                        } catch (IOException e) {
                            // Skip files we can't read
                        }
                    });
        } catch (IOException e) {
            response.put("success", false);
            response.put("message", "Error reading directory: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        if (fileList.isEmpty()) {
            response.put("success", true);
            response.put("message", "No log files found");
            response.put("files", new ArrayList<>());
            return ResponseEntity.ok(response);
        }

        response.put("success", true);
        response.put("count", fileList.size());
        response.put("files", fileList);
        return ResponseEntity.ok(response);
    }
}
