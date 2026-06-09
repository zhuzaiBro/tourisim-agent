-- ============================================================
-- Tourism RAG 系统初始化 SQL
-- 数据库: MySQL 8.0+
-- 字符集: utf8mb4 / utf8mb4_unicode_ci
-- 生成依据: JPA Entity 类反向推导
-- ============================================================

-- 创建数据库（首次部署时执行）
CREATE DATABASE IF NOT EXISTS tourism_rag
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE tourism_rag;

-- ------------------------------------------------------------
-- 1. 用户表 users
--    对应 entity: User.java
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `users` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `username`   VARCHAR(50)  NOT NULL                 COMMENT '用户名',
    `email`      VARCHAR(100) NOT NULL                 COMMENT '邮箱（唯一）',
    `password`   VARCHAR(255) NOT NULL                 COMMENT 'BCrypt 加密密码',
    `role`       VARCHAR(20)  NOT NULL DEFAULT 'USER'  COMMENT '角色：USER / ADMIN',
    `created_at` DATETIME                              COMMENT '创建时间',
    `updated_at` DATETIME                              COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_email` (`email`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='用户表';

-- ------------------------------------------------------------
-- 2. 城市表 city
--    对应 entity: City.java
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `city` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `code`              VARCHAR(50)  NOT NULL                 COMMENT '城市编码（小写英文，唯一）',
    `name_cn`           VARCHAR(50)  NOT NULL                 COMMENT '中文名',
    `name_en`           VARCHAR(100) NOT NULL                 COMMENT '英文名',
    `province`          VARCHAR(50)                           COMMENT '所属省份/直辖市',
    `description`       TEXT                                  COMMENT '城市简介',
    `cover_image`       VARCHAR(500)                          COMMENT '封面图 URL',
    `enabled`           TINYINT(1)   NOT NULL DEFAULT 0       COMMENT '是否启用（0=否 1=是）',
    `knowledge_ingested` TINYINT(1)  NOT NULL DEFAULT 0       COMMENT '知识库是否已摄入（0=否 1=是）',
    `created_at`        DATETIME                              COMMENT '创建时间',
    `updated_at`        DATETIME                              COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_city_code` (`code`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='城市表';

-- ------------------------------------------------------------
-- 3. 景点/兴趣点表 attraction
--    对应 entity: Attraction.java
--    category 枚举: attraction / food / transport / accommodation / festival
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `attraction` (
    `id`            BIGINT         NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `city_code`     VARCHAR(50)    NOT NULL                 COMMENT '所属城市编码（关联 city.code）',
    `name`          VARCHAR(200)   NOT NULL                 COMMENT '名称',
    `category`      VARCHAR(50)    NOT NULL                 COMMENT '分类：attraction/food/transport/accommodation/festival',
    `description`   TEXT                                    COMMENT '详细描述',
    `address`       VARCHAR(500)                            COMMENT '地址',
    `ticket_price`  DECIMAL(10, 2)                          COMMENT '门票价格（元，-1=免费）',
    `opening_hours` VARCHAR(200)                            COMMENT '开放时间描述',
    `seasons`       VARCHAR(100)                            COMMENT '适合季节（逗号分隔：spring,summer,autumn,winter）',
    `tags`          TEXT                                    COMMENT '适合人群标签（JSON 数组）',
    `rating`        DECIMAL(2, 1)                           COMMENT '评分（0.0 ~ 5.0）',
    `recommended`   TINYINT(1)     NOT NULL DEFAULT 0       COMMENT '是否精选推荐（0=否 1=是）',
    `created_at`    DATETIME                                COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_city_category` (`city_code`, `category`),
    INDEX `idx_city_code`     (`city_code`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='景点/兴趣点表';

-- ------------------------------------------------------------
-- 4. 会话表 conversations
--    对应 entity: Conversation.java
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `conversations` (
    `id`          VARCHAR(36)  NOT NULL                 COMMENT '主键（UUID）',
    `user_id`     BIGINT       NOT NULL                 COMMENT '所属用户 ID（关联 users.id）',
    `title`       VARCHAR(200) NOT NULL                 COMMENT '会话标题',
    `cities_json` VARCHAR(500)                          COMMENT '关联城市编码 JSON 数组，如 ["qingdao","beijing"]',
    `created_at`  DATETIME                              COMMENT '创建时间',
    `updated_at`  DATETIME                              COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_conv_user_id` (`user_id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='会话表';

-- ------------------------------------------------------------
-- 5. 会话消息表 conversation_messages
--    对应 entity: ConversationMessage.java
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `conversation_messages` (
    `id`              BIGINT   NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `conversation_id` VARCHAR(36) NOT NULL              COMMENT '所属会话 ID（关联 conversations.id）',
    `role`            VARCHAR(20) NOT NULL              COMMENT '角色：user / assistant',
    `content`         LONGTEXT    NOT NULL              COMMENT '消息内容',
    `sources_json`    TEXT                              COMMENT '来源引用 JSON（仅 assistant 消息）',
    `timestamp`       DATETIME                          COMMENT '消息时间',
    PRIMARY KEY (`id`),
    INDEX `idx_msg_conv_id` (`conversation_id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='会话消息表';

-- ------------------------------------------------------------
-- 6. 行程记录表 itinerary_records
--    对应 entity: ItineraryRecord.java
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `itinerary_records` (
    `id`            VARCHAR(36)   NOT NULL              COMMENT '主键（UUID）',
    `city_code`     VARCHAR(50)   NOT NULL              COMMENT '城市编码（关联 city.code）',
    `city_name`     VARCHAR(100)                        COMMENT '城市中文名（冗余，查询友好）',
    `start_date`    VARCHAR(10)                         COMMENT '出发日期（yyyy-MM-dd）',
    `end_date`      VARCHAR(10)                         COMMENT '结束日期（yyyy-MM-dd）',
    `total_days`    INT           NOT NULL DEFAULT 0    COMMENT '行程总天数',
    `response_json` LONGTEXT                            COMMENT '完整行程响应 JSON',
    `request_json`  VARCHAR(1000)                       COMMENT '请求参数 JSON（偏好/预算等）',
    `user_id`       BIGINT                              COMMENT '用户 ID（可为 NULL，支持匿名）',
    `created_at`    DATETIME                            COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_city_date` (`city_code`, `start_date`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='行程记录表';

-- ============================================================
-- 外键约束（可选，如需严格约束则取消注释）
-- 注意：JPA 默认不生成外键，启用外键需确认服务层逻辑完整
-- ============================================================
-- ALTER TABLE `conversations`
--     ADD CONSTRAINT `fk_conv_user`
--         FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;
--
-- ALTER TABLE `conversation_messages`
--     ADD CONSTRAINT `fk_msg_conv`
--         FOREIGN KEY (`conversation_id`) REFERENCES `conversations` (`id`) ON DELETE CASCADE;
--
-- ALTER TABLE `attraction`
--     ADD CONSTRAINT `fk_attr_city`
--         FOREIGN KEY (`city_code`) REFERENCES `city` (`code`) ON DELETE RESTRICT;
--
-- ALTER TABLE `itinerary_records`
--     ADD CONSTRAINT `fk_itinerary_user`
--         FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL;

-- ============================================================
-- 初始数据
-- ============================================================

-- 已启用城市（青岛，知识库已摄入）
INSERT INTO `city` (`code`, `name_cn`, `name_en`, `province`, `description`, `enabled`, `knowledge_ingested`, `created_at`, `updated_at`)
VALUES
('qingdao', '青岛', 'Qingdao', '山东省',
 '青岛是中国著名的海滨城市，以德式建筑、啤酒文化和优美海岸线著称。',
 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE `updated_at` = NOW();

-- 待扩展城市示例（enabled=0，知识库未摄入）
INSERT INTO `city` (`code`, `name_cn`, `name_en`, `province`, `enabled`, `knowledge_ingested`, `created_at`, `updated_at`)
VALUES
('beijing',   '北京', 'Beijing',   '北京市', 0, 0, NOW(), NOW()),
('shanghai',  '上海', 'Shanghai',  '上海市', 0, 0, NOW(), NOW()),
('chengdu',   '成都', 'Chengdu',   '四川省', 0, 0, NOW(), NOW()),
('hangzhou',  '杭州', 'Hangzhou',  '浙江省', 0, 0, NOW(), NOW()),
('xian',      '西安', 'Xi''an',    '陕西省', 0, 0, NOW(), NOW()),
('guilin',    '桂林', 'Guilin',    '广西壮族自治区', 0, 0, NOW(), NOW()),
('sanya',     '三亚', 'Sanya',     '海南省', 0, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE `updated_at` = NOW();
