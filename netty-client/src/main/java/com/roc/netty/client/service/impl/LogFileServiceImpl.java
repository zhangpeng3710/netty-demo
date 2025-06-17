package com.roc.netty.client.service.impl;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.util.OptionHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roc.netty.client.netty.NettyClient;
import com.roc.netty.client.service.LogFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogFileServiceImpl implements LogFileService {

    @Value("${logUpload.isCompressed:false}")
    private boolean beCompressed;

    @Value("${logUpload.bankAccount.isDesensitized:false}")
    private boolean beDesensitized;

    @Value("${logUpload.bankAccount.regex:(\\d{4})(\\d{4,10})(\\d{4})}")
    private String bankAccountRegex;

    private final NettyClient nettyClient;
    private final ObjectMapper objectMapper;

    private final String logRootDirectory = System.getProperty("user.dir")
            + File.separator
            + OptionHelper.substVars("${LOG_PATH}", (LoggerContext) LoggerFactory.getILoggerFactory());

    @Override
    public Map<String, Object> uploadLogFilesByDateRange(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> response = new HashMap<>();
        Path uploadPath = Paths.get(logRootDirectory);

        if (!Files.exists(uploadPath) || !Files.isDirectory(uploadPath)) {
            response.put("success", false);
            response.put("message", "Upload directory does not exist");
            return response;
        }


        // Set time to start and end of day for complete day coverage
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        List<Path> fileList;
        try (Stream<Path> walk = Files.walk(uploadPath)) {
            fileList = walk.filter(Files::isRegularFile)
                    .filter(path -> isLogFileInDateRange(path, startDate, endDate))
                    .collect(Collectors.toList());

            if (fileList.isEmpty()) {
                response.put("success", false);
                response.put("message", "No log files found in the specified date range");
                return response;
            }

            // Process the files...
            for (Path filePath : fileList) {
                boolean uploadSuccess = sendFileToServer(filePath);
                if (!uploadSuccess) {
                    response.put("success", false);
                    response.put("message", "Failed to send file: " + filePath);
                    return response;
                }
            }

            response.put("success", true);
            response.put("message", "Log files sent to server successfully");
            response.put("fileCount", fileList.size());
            return response;

        } catch (IOException e) {
            response.put("success", false);
            response.put("message", "Error reading directory: " + e.getMessage());
            return response;
        }
    }

    private boolean isLogFileInDateRange(Path path, LocalDate startDate, LocalDate endDate) {
        String fileName = path.getFileName().toString().toLowerCase();

        // Check file extension
        if (!fileName.endsWith(".log") && !fileName.endsWith(".gz")) {
            return false;
        }

        try {
            // Extract date from filename (assuming format like app-2023-01-01.log or 2023-01-01.log)
            Pattern pattern = Pattern.compile(".*(\\d{4}-\\d{2}-\\d{2}).*");
            Matcher matcher = pattern.matcher(fileName);

            if (matcher.find()) {
                String dateStr = matcher.group(1);
                LocalDate fileDate = LocalDate.parse(dateStr);
                return !fileDate.isBefore(startDate) && fileDate.isBefore(endDate.plusDays(1));
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Map<String, Object> uploadLogFileByPath(String relativePath) {
        Map<String, Object> response = new HashMap<>();

        try {
            Path fileToUpload = Paths.get(logRootDirectory + File.separator + relativePath);

            // Check if file exists and is readable
            if (!Files.exists(fileToUpload) || !Files.isReadable(fileToUpload)) {
                response.put("success", false);
                response.put("message", "File not found or not readable: " + relativePath);
                return response;
            }

            // Send file to Netty server
            boolean uploadSuccess = sendFileToServer(fileToUpload);

            if (!uploadSuccess) {
                throw new IOException("Failed to send file to server");
            }

            // Prepare success response
            response.put("success", true);
            response.put("message", "Log file sent to server successfully");
            response.put("filename", fileToUpload.getFileName().toString());
            response.put("size", Files.size(fileToUpload));
            response.put("path", fileToUpload.toString());

            return response;

        } catch (IOException e) {
            response.put("success", false);
            response.put("message", "Failed to process file: " + e.getMessage());
            return response;
        }
    }


    @Override
    public Map<String, Object> listLogFiles() {
        Map<String, Object> response = new HashMap<>();
        Path uploadPath = Paths.get(logRootDirectory);

        if (!Files.exists(uploadPath) || !Files.isDirectory(uploadPath)) {
            response.put("success", false);
            response.put("message", "Upload directory does not exist");
            return response;
        }

        List<Map<String, Object>> fileList = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(uploadPath)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".log"))
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
            return response;
        }

        response.put("success", true);
        response.put("count", fileList.size());
        response.put("files", fileList);
        return response;
    }

    @Override
    public boolean sendFileToServer(Path filePath) {
        try {
            // Read file content as text for masking
            String fileContent = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            String fileName = filePath.getFileName().toString();

            // Mask bank account numbers if enabled
            if (beDesensitized) {
                fileContent = maskBankAccounts(fileContent);
            }

            // Convert back to bytes for compression
            byte[] contentBytes = fileContent.getBytes(StandardCharsets.UTF_8);
            Map<String, Object> fileData = new HashMap<>();

            fileData.put("fileName", fileName);
            fileData.put("originalSize", contentBytes.length);

            if (beCompressed) {
                // Compress the content
                byte[] compressedContent = compress(contentBytes);
                fileData.put("compressedSize", compressedContent.length);
                fileData.put("content", Base64.getEncoder().encodeToString(compressedContent));
                fileData.put("beCompressed", true);
            } else {
                // No compression
                fileData.put("content", fileContent);
                fileData.put("beCompressed", false);
            }

            // Convert to JSON string and send
            String jsonData = objectMapper.writeValueAsString(fileData);
            return nettyClient.sendMessage(jsonData, true);

        } catch (IOException e) {
            log.error("Error processing or sending file to server: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Masks bank account numbers in the content (keeps first 4 and last 4 digits)
     */
    private String maskBankAccounts(String content) {
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

}
