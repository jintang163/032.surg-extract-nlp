-- ============================================================
-- 批量处理任务相关表
-- 数据库: surg_extract_nlp
-- 字符集: utf8mb4
-- ============================================================

USE `surg_extract_nlp`;

-- -----------------------------------------------------------
-- 18. 批量任务表
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `batch_task`;
CREATE TABLE `batch_task` (
    `id`                  BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_name`           VARCHAR(128)    NOT NULL COMMENT '任务名称',
    `department`          VARCHAR(128)    DEFAULT NULL COMMENT '所属科室',
    `task_type`           VARCHAR(32)     NOT NULL DEFAULT 'SURGERY_RECORD' COMMENT '任务类型: SURGERY_RECORD-手术记录',
    `original_file_name`  VARCHAR(255)    DEFAULT NULL COMMENT '原始ZIP文件名',
    `file_path`           VARCHAR(512)    DEFAULT NULL COMMENT 'ZIP文件存储路径',
    `file_size`           BIGINT          DEFAULT 0 COMMENT 'ZIP文件大小(字节)',
    `total_count`         INT             NOT NULL DEFAULT 0 COMMENT '总文件数',
    `success_count`       INT             NOT NULL DEFAULT 0 COMMENT '成功数',
    `failed_count`        INT             NOT NULL DEFAULT 0 COMMENT '失败数',
    `pending_count`       INT             NOT NULL DEFAULT 0 COMMENT '待处理数',
    `status`              VARCHAR(32)     NOT NULL DEFAULT 'PENDING' COMMENT '任务状态: PENDING-待处理, PROCESSING-处理中, COMPLETED-已完成(全部成功), PARTIAL-部分完成, FAILED-全部失败',
    `error_message`       VARCHAR(512)    DEFAULT NULL COMMENT '错误信息',
    `notify_type`         VARCHAR(32)     NOT NULL DEFAULT 'EMAIL' COMMENT '通知类型: EMAIL-邮件',
    `notify_target`       VARCHAR(255)    DEFAULT NULL COMMENT '通知目标(邮箱地址)',
    `notified`            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否已通知: 0-否, 1-是',
    `retry_count`         INT             NOT NULL DEFAULT 0 COMMENT '重试次数',
    `max_retry_count`     INT             NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    `start_time`          DATETIME        DEFAULT NULL COMMENT '开始时间',
    `end_time`            DATETIME        DEFAULT NULL COMMENT '结束时间',
    `created_by`          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    `created_by_name`     VARCHAR(64)     DEFAULT NULL COMMENT '创建人姓名',
    `created_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_department` (`department`),
    KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批量任务表';

-- -----------------------------------------------------------
-- 19. 批量任务明细表
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `batch_task_item`;
CREATE TABLE `batch_task_item` (
    `id`                  BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`             BIGINT          NOT NULL COMMENT '批量任务ID(关联batch_task)',
    `record_id`           BIGINT          DEFAULT NULL COMMENT '关联的手术记录ID',
    `file_name`           VARCHAR(255)    NOT NULL COMMENT '文件名',
    `file_path`           VARCHAR(512)    DEFAULT NULL COMMENT '文件存储路径',
    `patient_name`        VARCHAR(64)     DEFAULT NULL COMMENT '患者姓名',
    `hospital_no`         VARCHAR(64)     DEFAULT NULL COMMENT '住院号',
    `file_type`           VARCHAR(32)     NOT NULL COMMENT '文件类型: TEXT, WORD, PDF, IMAGE',
    `status`              VARCHAR(32)     NOT NULL DEFAULT 'PENDING' COMMENT '处理状态: PENDING-待处理, PROCESSING-处理中, SUCCESS-成功, FAILED-失败',
    `error_message`       VARCHAR(512)    DEFAULT NULL COMMENT '错误信息',
    `retry_count`         INT             NOT NULL DEFAULT 0 COMMENT '重试次数',
    `start_time`          DATETIME        DEFAULT NULL COMMENT '开始时间',
    `end_time`            DATETIME        DEFAULT NULL COMMENT '结束时间',
    `created_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_status` (`status`),
    KEY `idx_record_id` (`record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批量任务明细表';
