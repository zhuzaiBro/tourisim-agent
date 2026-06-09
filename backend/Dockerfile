# ============================================
# 多阶段构建：减小最终镜像体积
# ============================================

# 构建阶段
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Alpine 镜像默认不带 Maven
RUN apk add --no-cache maven

# 先复制 pom.xml，利用 Docker 层缓存（依赖不变时跳过下载）
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B

# 复制源码并构建
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -B

# ============================================
# 运行阶段：使用 JRE（更小）
# ============================================
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# 创建非 root 用户（安全最佳实践）
RUN addgroup -S tourism && adduser -S tourism -G tourism

# 从构建阶段复制 jar
COPY --from=builder /app/target/tourism-rag-*.jar app.jar

# 创建知识库文件目录
RUN mkdir -p /app/knowledge && chown -R tourism:tourism /app

USER tourism

# JVM 调优参数
# -XX:+UseZGC: 使用 ZGC 低延迟垃圾收集器（Java 21 稳定版）
# -XX:MaxRAMPercentage=75: 容器内存的 75% 给堆
ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dfile.encoding=UTF-8", \
    "-jar", "app.jar"]

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD wget -qO- http://localhost:8080/api/cities || exit 1
