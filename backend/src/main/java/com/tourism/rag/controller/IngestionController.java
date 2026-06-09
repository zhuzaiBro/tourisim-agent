package com.tourism.rag.controller;

import com.tourism.rag.dto.IngestRequest;
import com.tourism.rag.service.IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 数据摄入控制器
 *
 * API：
 * - POST /api/ingest/qingdao          触发青岛知识库摄入（内置数据）
 * - POST /api/ingest/city             触发任意城市摄入（通用接口，服务器路径/MySQL/内置）
 * - POST /api/ingest/upload           上传文件并摄入知识库（multipart/form-data）
 * - POST /api/ingest/db/{city}        从 MySQL 摄入景点数据
 *
 * ⚠️ 生产环境应添加权限控制（管理员 Token 校验）
 */
@Slf4j
@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionService ingestionService;

    /**
     * 初始化青岛内置知识库
     */
    @PostMapping("/qingdao")
    public ResponseEntity<Map<String, Object>> ingestQingdao() {
        log.info("触发青岛知识库摄入...");
        String jobId = ingestionService.startJob("qingdao", "青岛知识库摄入中");
        ingestionService.ingestQingdaoData();
        return ResponseEntity.accepted().body(Map.of(
                "message", "青岛知识库摄入任务已启动，请稍后查看日志",
                "status", "processing",
                "jobId", jobId,
                "cityCode", "qingdao"
        ));
    }

    /**
     * 删除指定城市的 Milvus 向量（不删除 MySQL 城市/景点数据）。
     */
    @DeleteMapping("/city/{cityCode}")
    public ResponseEntity<Map<String, Object>> deleteCityVectors(@PathVariable String cityCode) {
        ingestionService.deleteVectorsByCity(cityCode);
        return ResponseEntity.ok(Map.of(
                "message", "城市向量已删除",
                "cityCode", cityCode,
                "status", "deleted"
        ));
    }

    /**
     * 重建索引：先删向量再重新摄入（青岛走 md；其他城市走 MySQL attraction）。
     */
    @PostMapping("/reindex/{cityCode}")
    public ResponseEntity<Map<String, Object>> reindexCity(@PathVariable String cityCode) {
        String jobId = ingestionService.startJob(cityCode, "重建索引中");
        ingestionService.reindexCity(cityCode, jobId);
        return ResponseEntity.accepted().body(Map.of(
                "message", "重建索引任务已启动",
                "cityCode", cityCode,
                "jobId", jobId,
                "status", "processing"
        ));
    }

    /**
     * 查询异步摄入/重建任务状态。
     */
    @GetMapping("/status/{jobId}")
    public ResponseEntity<?> ingestStatus(@PathVariable String jobId) {
        return ingestionService.getJob(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 通用城市数据摄入接口（服务器路径 / MySQL / 内置）
     */
    @PostMapping("/city")
    public ResponseEntity<Map<String, Object>> ingestCity(@RequestBody IngestRequest request) {
        log.info("触发城市数据摄入: {}, 类型: {}", request.getCityCode(), request.getSourceType());

        switch (request.getSourceType()) {
            case FILE -> {
                Path filePath = Path.of(request.getFilePath());
                if (!Files.exists(filePath)) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "文件不存在: " + request.getFilePath()
                    ));
                }
                ingestionService.ingestFromFile(request.getCityCode(), "knowledge", filePath);
            }
            case MYSQL -> ingestionService.ingestFromDatabase(request.getCityCode());
            case BUILTIN -> {
                if ("qingdao".equals(request.getCityCode())) {
                    ingestionService.ingestQingdaoData();
                } else {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "暂无该城市的内置数据，请使用 FILE 类型提供知识库文件"
                    ));
                }
            }
            default -> {
                return ResponseEntity.badRequest().body(Map.of("error", "不支持的数据源类型"));
            }
        }

        return ResponseEntity.accepted().body(Map.of(
                "message", String.format("城市 [%s] 数据摄入任务已启动", request.getCityCode()),
                "cityCode", request.getCityCode(),
                "status", "processing"
        ));
    }

    /**
     * 上传文件并触发知识库摄入
     *
     * 支持格式：.md / .txt / .pdf
     * 参数：
     *   file      - 上传的文件（必填）
     *   cityCode  - 目标城市编码（必填）
     *   category  - 知识类别，默认 knowledge（可选：attraction/food/transport/accommodation/festival）
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadAndIngest(
            @RequestParam("file") MultipartFile file,
            @RequestParam("cityCode") String cityCode,
            @RequestParam(value = "category", defaultValue = "knowledge") String category
    ) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "上传文件不能为空"));
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件名无效"));
        }

        // 校验文件类型
        String lower = originalName.toLowerCase();
        if (!lower.endsWith(".md") && !lower.endsWith(".txt") && !lower.endsWith(".pdf")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "仅支持 .md / .txt / .pdf 格式文件"
            ));
        }

        // 保存到临时目录
        Path tempDir = Files.createTempDirectory("tourism-rag-upload-");
        Path tempFile = tempDir.resolve(originalName);
        Files.copy(file.getInputStream(), tempFile);

        log.info("文件上传成功: {}, 城市: {}, 分类: {}", originalName, cityCode, category);

        // 异步触发摄入
        ingestionService.ingestFromFile(cityCode, category, tempFile);

        return ResponseEntity.accepted().body(Map.of(
                "message", String.format("文件 [%s] 上传成功，城市 [%s] 知识库摄入任务已启动", originalName, cityCode),
                "cityCode", cityCode,
                "fileName", originalName,
                "category", category,
                "status", "processing"
        ));
    }

    /**
     * 从 MySQL attraction 表向量化摄入指定城市景点数据
     */
    @PostMapping("/db/{cityCode}")
    public ResponseEntity<Map<String, Object>> ingestFromDatabase(@PathVariable String cityCode) {
        ingestionService.ingestFromDatabase(cityCode);
        return ResponseEntity.accepted().body(Map.of(
                "message", String.format("城市 [%s] 数据库数据摄入已启动", cityCode),
                "cityCode", cityCode
        ));
    }
}
