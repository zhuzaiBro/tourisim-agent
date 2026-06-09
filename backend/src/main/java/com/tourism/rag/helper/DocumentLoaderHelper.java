package com.tourism.rag.helper;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档加载助手
 *
 * 【metadata 规范 - 核心！】
 * 每个文档块必须附加以下 metadata，用于 Milvus 过滤：
 *
 *   city      : 城市编码（"qingdao" / "beijing" 等）       ← 最重要，用于城市隔离查询
 *   category  : 分类（attraction/food/transport/accommodation/festival）
 *   season    : 适合季节（spring/summer/autumn/winter/all）
 *   source    : 数据来源（文件名/URL/数据库表名）
 *   source_type: 来源类型（markdown/pdf/web/database）
 *   language  : 语言（zh/en）
 *   ingested_at: 摄入日期（格式 yyyy-MM-dd）
 *
 * 过滤示例（在 RetrievalService 中使用）：
 *   // 单城市
 *   Filter filter = metadataKey("city").isEqualTo("qingdao");
 *   // 城市+分类
 *   Filter filter = and(
 *       metadataKey("city").isEqualTo("qingdao"),
 *       metadataKey("category").isEqualTo("food")
 *   );
 */
@Slf4j
@Component
public class DocumentLoaderHelper {

    /**
     * 从本地文件加载文档（支持 .txt / .md / .pdf）
     *
     * @param filePath 文件路径
     * @param city     城市编码
     * @param category 分类
     * @return 附带 metadata 的 Document
     */
    public Document loadFromFile(Path filePath, String city, String category) {
        String fileName = filePath.getFileName().toString();
        String extension = getExtension(fileName);

        DocumentParser parser = switch (extension) {
            case "pdf" -> new ApachePdfBoxDocumentParser();
            default -> new TextDocumentParser();  // md / txt 等文本格式
        };

        Document doc = FileSystemDocumentLoader.loadDocument(filePath, parser);

        // 附加标准 metadata
        return Document.from(doc.text(), buildMetadata(city, category, fileName, "file", "all"));
    }

    private static final Pattern SECTION_HEADER = Pattern.compile("(?m)^##\\s+(.+)$");

    /**
     * 从 classpath 加载 Markdown，按二级标题（##）拆分为多文档并附加 metadata。
     */
    public List<Document> loadMarkdownSectionsFromClasspath(String classpathResource, String city) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalStateException("资源不存在: " + classpathResource);
            }
            String full = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return splitMarkdownSections(full, city, classpathResource);
        } catch (IOException e) {
            throw new IllegalStateException("读取资源失败: " + classpathResource, e);
        }
    }

    List<Document> splitMarkdownSections(String markdown, String city, String sourceFile) {
        List<Document> docs = new ArrayList<>();
        Matcher matcher = SECTION_HEADER.matcher(markdown);
        List<int[]> sections = new ArrayList<>();
        List<String> titles = new ArrayList<>();

        while (matcher.find()) {
            sections.add(new int[]{matcher.start(), matcher.end()});
            titles.add(matcher.group(1).trim());
        }

        for (int i = 0; i < sections.size(); i++) {
            int bodyStart = sections.get(i)[1];
            int bodyEnd = (i + 1 < sections.size()) ? sections.get(i + 1)[0] : markdown.length();
            String title = titles.get(i);
            String body = markdown.substring(bodyStart, bodyEnd).trim();
            if (body.isBlank()) {
                continue;
            }
            String category = resolveCategory(title);
            String text = "## " + title + "\n\n" + body;
            docs.add(createFromText(text, city, category, sourceFile + "#" + title, "all"));
        }

        if (docs.isEmpty()) {
            docs.add(createFromText(markdown, city, "knowledge", sourceFile, "all"));
        }
        return docs;
    }

    private String resolveCategory(String sectionTitle) {
        Matcher m = Pattern.compile("category:\\s*(\\w+)", Pattern.CASE_INSENSITIVE).matcher(sectionTitle);
        if (m.find()) {
            return m.group(1).toLowerCase();
        }
        String lower = sectionTitle.toLowerCase();
        if (lower.contains("美食") || lower.contains("food")) return "food";
        if (lower.contains("交通") || lower.contains("transport")) return "transport";
        if (lower.contains("住宿") || lower.contains("hotel")) return "accommodation";
        if (lower.contains("景点") || lower.contains("attraction")) return "attraction";
        return "knowledge";
    }

    /**
     * 从原始文本创建文档（用于硬编码示例数据）
     *
     * @param text     文档文本内容
     * @param city     城市编码（如 "qingdao"）
     * @param category 分类（如 "attraction"）
     * @param source   来源描述（如 "青岛旅游官网"）
     * @param season   适合季节（如 "summer" 或 "all"）
     * @return 附带 metadata 的 Document
     */
    public Document createFromText(String text, String city, String category,
                                   String source, String season) {
        Metadata metadata = buildMetadata(city, category, source, "builtin", season);
        return Document.from(text, metadata);
    }

    /**
     * 从数据库记录（Map）创建文档
     * 用于将 MySQL attraction 表内容向量化存入 Milvus
     */
    public Document createFromDatabaseRecord(Map<String, Object> record, String city) {
        // 将结构化数据拼接为自然语言文本，提高语义检索效果
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(record.getOrDefault("category", "景点")).append("】");
        sb.append(record.getOrDefault("name", "")).append("\n");
        sb.append("简介：").append(record.getOrDefault("description", "")).append("\n");
        if (record.containsKey("address")) {
            sb.append("地址：").append(record.get("address")).append("\n");
        }
        if (record.containsKey("ticketPrice")) {
            Object price = record.get("ticketPrice");
            sb.append("门票：").append(price != null ? price + "元" : "免费").append("\n");
        }
        if (record.containsKey("openingHours")) {
            sb.append("开放时间：").append(record.get("openingHours")).append("\n");
        }

        String category = String.valueOf(record.getOrDefault("category", "attraction"));
        String seasons = String.valueOf(record.getOrDefault("seasons", "all"));
        String source = "mysql:attraction:" + record.getOrDefault("id", "");

        return Document.from(sb.toString(), buildMetadata(city, category, source, "database", seasons));
    }

    // ---- 内部方法 ----

    /**
     * 构建标准 metadata
     *
     * 【重要】所有字段名必须与 Milvus 过滤器中使用的字段名完全一致
     */
    private Metadata buildMetadata(String city, String category, String source,
                                   String sourceType, String season) {
        Map<String, String> map = new HashMap<>();
        map.put("city", city.toLowerCase().trim());           // 城市编码（小写）
        map.put("category", category.toLowerCase().trim());   // 分类
        map.put("source", source);                            // 来源
        map.put("source_type", sourceType);                   // 来源类型
        map.put("season", season.toLowerCase().trim());       // 季节
        map.put("language", "zh");                            // 语言
        map.put("ingested_at", LocalDate.now().toString());   // 摄入日期
        return Metadata.from(map);
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex + 1).toLowerCase() : "";
    }
}
