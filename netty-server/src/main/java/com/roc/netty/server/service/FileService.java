package com.roc.netty.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.zip.GZIPInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final ObjectMapper objectMapper;

    /**
     * 处理上传的文件
     *
     * @param content 文件内容（Base64编码）
     * @return 处理结果信息
     */
    public String processUploadedFile(byte[] content) throws IOException {
        GZIPInputStream gzipIn = null;
        ByteArrayOutputStream bos = null;
        ByteArrayInputStream bis = null;

        try {
            // 解析文件信息
            HashMap<String, Object> fileInfo = objectMapper.readValue(content, HashMap.class);
            byte[] base64Content;
            String filename = fileInfo.get("fileName").toString();
            int originalSize = ((Number) fileInfo.get("originalSize")).intValue();
            boolean beCompressed = Boolean.TRUE.equals(fileInfo.get("beCompressed"));

            // 如果是压缩过的内容，直接使用二进制数据
            base64Content = fileInfo.get("content").toString().getBytes(StandardCharsets.UTF_8);


            // 解码Base64数据
            byte[] compressedData = Base64.getDecoder().decode(base64Content);

            // 解压数据
            bis = new ByteArrayInputStream(compressedData);
            gzipIn = new GZIPInputStream(bis);
            bos = new ByteArrayOutputStream();

            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIn.read(buffer)) > 0) {
                // 将buffer
                bos.write(buffer, 0, len);
            }

            byte[] decompressedData = bos.toByteArray();

            // 创建保存目录
            String savePath = "logs/uploaded/" + filename;
            Path saveDir = Paths.get("logs/uploaded");
            if (!Files.exists(saveDir)) {
                Files.createDirectories(saveDir);
            }

            // 保存文件
            Files.write(Paths.get(savePath), decompressedData);
            log.info("File saved successfully: {}", savePath);

            return "File received and saved: " + savePath;

        } finally {
            // 关闭资源
            if (gzipIn != null) {
                try {
                    gzipIn.close();
                } catch (IOException e) {
                    log.error("Error closing GZIPInputStream", e);
                }
            }
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e) {
                    log.error("Error closing ByteArrayOutputStream", e);
                }
            }
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    log.error("Error closing ByteArrayInputStream", e);
                }
            }
        }
    }
}
