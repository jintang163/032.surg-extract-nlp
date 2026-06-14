-- ============================================================
-- 医生反馈与主动学习模块 数据库表
-- ============================================================

-- -----------------------------------------------------------
-- 13. 医生反馈表
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `doctor_feedback`;
CREATE TABLE `doctor_feedback` (
    `id`                      BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `record_id`               BIGINT          NOT NULL COMMENT '手术记录ID',
    `record_no`             VARCHAR(64)     DEFAULT NULL COMMENT '手术记录编号',
    `entity_id`               BIGINT          DEFAULT NULL COMMENT '实体ID',
    `entity_type`           VARCHAR(64)     NOT NULL COMMENT '实体类型',
    `original_value`        VARCHAR(512)    DEFAULT NULL COMMENT '模型原始值',
    `original_unit`         VARCHAR(32)     DEFAULT NULL COMMENT '原始单位',
    `original_confidence`   DECIMAL(5,4)   DEFAULT NULL COMMENT '模型原始置信度',
    `original_source`       VARCHAR(32)     DEFAULT NULL COMMENT '原始来源: MODEL/REGEX/RULE',
    `original_start_pos`   INT             DEFAULT NULL COMMENT '原始起始位置',
    `original_end_pos`     INT             DEFAULT NULL COMMENT '原始结束位置',
    `original_text`        VARCHAR(512)    DEFAULT NULL COMMENT '原文片段(含上下文)',
    `corrected_value`       VARCHAR(512)    DEFAULT NULL COMMENT '医生修正后的值',
    `corrected_unit`        VARCHAR(32)     DEFAULT NULL COMMENT '修正后单位',
    `correction_type`     VARCHAR(32)     DEFAULT NULL COMMENT '修正类型: CORRECTION-修改, ADDITION-新增, DELETION-删除',
    `feedback_source`     VARCHAR(32)     DEFAULT NULL COMMENT '反馈来源: ENTITY_EDIT-实体编辑, HOME_PAGE-首页填写, MANUAL-手动提交',
    `department`          VARCHAR(128)    DEFAULT NULL COMMENT '科室',
    `feedback_user_id`    BIGINT          DEFAULT NULL COMMENT '反馈人ID',
    `feedback_user_name`  VARCHAR(64)     DEFAULT NULL COMMENT '反馈人姓名',
    `feedback_remark`    VARCHAR(512)    DEFAULT NULL COMMENT '反馈备注',
    `quality_score`        INT             DEFAULT NULL COMMENT '质量评分 0-100',
    `used_for_training`  TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已用于训练: 0-未使用, 1-已使用',
    `used_time`            DATETIME        DEFAULT NULL COMMENT '使用时间',
    `train_batch_no`       VARCHAR(64)     DEFAULT NULL COMMENT '训练批次号',
    `created_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`              TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_record_id` (`record_id`),
    KEY `idx_entity_type` (`entity_type`),
    KEY `idx_used_for_training` (`used_for_training`),
    KEY `idx_created_time` (`created_time`),
    KEY `idx_feedback_user_id` (`feedback_user_id`),
    KEY `idx_department` (`department`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='医生反馈表';

-- -----------------------------------------------------------
-- 14. 模型训练日志表
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `model_train_log`;
CREATE TABLE `model_train_log` (
    `id`                      BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `train_batch_no`         VARCHAR(64)     NOT NULL COMMENT '训练批次号',
    `model_name`             VARCHAR(128)    DEFAULT NULL COMMENT '模型名称',
    `model_version`         VARCHAR(64)     DEFAULT NULL COMMENT '模型版本号',
    `previous_version`        VARCHAR(64)     DEFAULT NULL COMMENT '上一版本号',
    `train_type`             VARCHAR(32)     NOT NULL DEFAULT 'INCREMENTAL' COMMENT '训练类型: FULL-全量, INCREMENTAL-增量, WEEKLY-周度',
    `feedback_count`         INT             DEFAULT 0 COMMENT '使用的反馈数据量',
    `new_sample_count`    INT             DEFAULT 0 COMMENT '新增训练样本数',
    `total_sample_count`    INT             DEFAULT 0 COMMENT '总训练样本数',
    `train_loss`             DECIMAL(10,6)   DEFAULT NULL COMMENT '训练损失',
    `dev_loss`              DECIMAL(10,6)   DEFAULT NULL COMMENT '验证损失',
    `precision_score`      DECIMAL(5,4)   DEFAULT NULL COMMENT '精确率',
    `recall_score`          DECIMAL(5,4)   DEFAULT NULL COMMENT '召回率',
    `f1_score`              DECIMAL(5,4)   DEFAULT NULL COMMENT 'F1分数',
    `previous_f1_score`     DECIMAL(5,4)   DEFAULT NULL COMMENT '上一版本F1分数',
    `f1_improvement`     DECIMAL(5,4)   DEFAULT NULL COMMENT 'F1提升幅度',
    `entity_type_breakdown` TEXT            DEFAULT NULL COMMENT '各实体类型指标明细(JSON)',
    `train_status`          VARCHAR(32)     NOT NULL DEFAULT 'PENDING' COMMENT '训练状态: PENDING-待开始, RUNNING-训练中, SUCCESS-成功, FAILED-失败',
    `fail_reason`         VARCHAR(512)    DEFAULT NULL COMMENT '失败原因',
    `train_start_time`     DATETIME        DEFAULT NULL COMMENT '训练开始时间',
    `train_end_time`        DATETIME        DEFAULT NULL COMMENT '训练结束时间',
    `train_duration_sec` INT             DEFAULT NULL COMMENT '训练耗时(秒)',
    `train_params`           TEXT            DEFAULT NULL COMMENT '训练参数(JSON)',
    `triggered_by`          BIGINT          DEFAULT NULL COMMENT '触发人ID',
    `triggered_by_name`  VARCHAR(64)     DEFAULT NULL COMMENT '触发人姓名',
    `model_path`            VARCHAR(512)    DEFAULT NULL COMMENT '模型存储路径',
    `remark`                VARCHAR(512)    DEFAULT NULL COMMENT '备注',
    `created_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`              TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_train_batch_no` (`train_batch_no`),
    KEY `idx_train_status` (`train_status`),
    KEY `idx_train_start_time` (`train_start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型训练日志表';
