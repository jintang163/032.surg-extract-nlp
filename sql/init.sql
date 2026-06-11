-- ============================================================
-- 手术记录结构化提取系统 数据库初始化脚本
-- 数据库: surg_extract_nlp
-- 字符集: utf8mb4
-- ============================================================

CREATE DATABASE IF NOT EXISTS `surg_extract_nlp` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `surg_extract_nlp`;

-- -----------------------------------------------------------
-- 1. 用户表
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username`      VARCHAR(64)     NOT NULL COMMENT '登录账号',
    `password`      VARCHAR(128)    NOT NULL COMMENT '密码(BCrypt加密)',
    `real_name`     VARCHAR(64)     NOT NULL COMMENT '真实姓名',
    `role`          VARCHAR(32)     NOT NULL DEFAULT 'DOCTOR' COMMENT '角色: ADMIN-管理员, DOCTOR-医生, NURSE-护士',
    `department`    VARCHAR(128)    DEFAULT NULL COMMENT '所属科室',
    `title`         VARCHAR(64)     DEFAULT NULL COMMENT '职称',
    `phone`         VARCHAR(32)     DEFAULT NULL COMMENT '联系电话',
    `email`         VARCHAR(128)    DEFAULT NULL COMMENT '邮箱',
    `status`        TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `last_login_time` DATETIME      DEFAULT NULL COMMENT '最后登录时间',
    `created_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- -----------------------------------------------------------
-- 2. 手术记录表
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `surgery_record`;
CREATE TABLE `surgery_record` (
    `id`                  BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `record_no`           VARCHAR(64)     NOT NULL COMMENT '手术记录编号(业务主键)',
    `patient_id`          VARCHAR(64)     DEFAULT NULL COMMENT '患者ID(关联HIS)',
    `patient_name`        VARCHAR(64)     DEFAULT NULL COMMENT '患者姓名',
    `hospital_no`         VARCHAR(64)     DEFAULT NULL COMMENT '住院号',
    `gender`              VARCHAR(8)      DEFAULT NULL COMMENT '性别: 男/女',
    `age`                 INT             DEFAULT NULL COMMENT '年龄',
    `department`          VARCHAR(128)    DEFAULT NULL COMMENT '就诊科室',
    `original_file_name`  VARCHAR(255)    DEFAULT NULL COMMENT '原始文件名',
    `file_type`           VARCHAR(32)     NOT NULL COMMENT '文件类型: TEXT, WORD, PDF, IMAGE, AUDIO, VIDEO',
    `file_path`           VARCHAR(512)    DEFAULT NULL COMMENT '文件存储路径',
    `file_size`           BIGINT          DEFAULT 0 COMMENT '文件大小(字节)',
    `ocr_text`            LONGTEXT        DEFAULT NULL COMMENT 'OCR识别原始文本',
    `processed_text`      LONGTEXT        DEFAULT NULL COMMENT '预处理后文本',
    `asr_text`            LONGTEXT        DEFAULT NULL COMMENT 'ASR语音转写文本',
    `audio_duration`      DECIMAL(10,2)   DEFAULT NULL COMMENT '音频时长(秒)',
    `asr_segments`        JSON            DEFAULT NULL COMMENT 'ASR分段结果(JSON)',
    `enhanced_text`       LONGTEXT        DEFAULT NULL COMMENT '多模态融合增强文本',
    `instruments`         JSON            DEFAULT NULL COMMENT '识别到的手术器械列表(JSON)',
    `fusion_stats`        JSON            DEFAULT NULL COMMENT '多模态融合统计信息(JSON)',
    `multimodal_status`   VARCHAR(32)     DEFAULT NULL COMMENT '多模态处理状态: NONE-无, ASR_DONE-语音完成, INSTRUMENT_DONE-器械完成, FUSED-融合完成, FUSION_FAILED-融合失败',
    `upload_user_id`      BIGINT          DEFAULT NULL COMMENT '上传人ID',
    `upload_user_name`    VARCHAR(64)     DEFAULT NULL COMMENT '上传人姓名',
    `upload_time`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `process_status`      VARCHAR(32)     NOT NULL DEFAULT 'PENDING' COMMENT '处理状态: PENDING-待处理, OCR_PROCESSING-OCR处理中, ASR_PROCESSING-语音识别中, NER_PROCESSING-实体抽取中, NER_DONE-抽取完成, COMPLETED-已完成, FAILED-失败',
    `process_message`     VARCHAR(512)    DEFAULT NULL COMMENT '处理信息/错误信息',
    `ocr_start_time`      DATETIME        DEFAULT NULL COMMENT 'OCR开始时间',
    `ocr_end_time`        DATETIME        DEFAULT NULL COMMENT 'OCR完成时间',
    `ner_start_time`      DATETIME        DEFAULT NULL COMMENT '实体抽取开始时间',
    `ner_end_time`        DATETIME        DEFAULT NULL COMMENT '实体抽取完成时间',
    `patient_confirmed`   TINYINT         DEFAULT 0 COMMENT '是否已确认病案首页: 0-未确认, 1-已确认',
    `confirm_user_id`     BIGINT          DEFAULT NULL COMMENT '确认人ID',
    `confirm_time`        DATETIME        DEFAULT NULL COMMENT '确认时间',
    `fill_duration`       INT             DEFAULT NULL COMMENT '填写用时(秒)',
    `manual_duration_est` INT             DEFAULT 600 COMMENT '人工录入预估用时(秒), 默认10分钟',
    `his_synced`          TINYINT         DEFAULT 0 COMMENT '是否已同步HIS: 0-未同步, 1-已同步',
    `his_sync_time`       DATETIME        DEFAULT NULL COMMENT 'HIS同步时间',
    `his_sync_message`    VARCHAR(512)    DEFAULT NULL COMMENT 'HIS同步信息',
    `created_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_record_no` (`record_no`),
    KEY `idx_patient_id` (`patient_id`),
    KEY `idx_hospital_no` (`hospital_no`),
    KEY `idx_upload_time` (`upload_time`),
    KEY `idx_process_status` (`process_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手术记录表';

-- -----------------------------------------------------------
-- 2-1. 手术记录附件表
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `surgery_record_attachment`;
CREATE TABLE `surgery_record_attachment` (
    `id`                  BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `record_id`           BIGINT          NOT NULL COMMENT '手术记录ID',
    `original_file_name`  VARCHAR(255)    NOT NULL COMMENT '原始文件名',
    `file_path`           VARCHAR(512)    NOT NULL COMMENT '文件存储路径',
    `file_type`           VARCHAR(32)     NOT NULL COMMENT '文件类型: TEXT, WORD, PDF, IMAGE, AUDIO, VIDEO',
    `file_size`           BIGINT          DEFAULT NULL COMMENT '文件大小(字节)',
    `attachment_type`     VARCHAR(32)     NOT NULL DEFAULT 'OTHER' COMMENT '附件类型: MAIN-主文档, ASR-语音旁白, INSTRUMENT-器械图谱, OTHER-其他',
    `process_status`      VARCHAR(32)     NOT NULL DEFAULT 'PENDING' COMMENT '处理状态: PENDING-待处理, PROCESSING-处理中, SUCCESS-成功, FAILED-失败',
    `process_message`     VARCHAR(512)    DEFAULT NULL COMMENT '处理信息',
    `extracted_text`      LONGTEXT        DEFAULT NULL COMMENT '提取的文本(OCR/ASR)',
    `sort_order`          INT             DEFAULT 0 COMMENT '排序号',
    `created_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_record_id` (`record_id`),
    KEY `idx_attachment_type` (`attachment_type`),
    KEY `idx_process_status` (`process_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手术记录附件表';

-- -----------------------------------------------------------
-- 3. 实体抽取结果表
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `surgery_entity`;
CREATE TABLE `surgery_entity` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `record_id`         BIGINT          NOT NULL COMMENT '手术记录ID',
    `entity_type`       VARCHAR(64)     NOT NULL COMMENT '实体类型: PATIENT_NAME, HOSPITAL_NO, SURGERY_DATE, SURGERY_NAME, INCISION_LEVEL, ANESTHESIA_TYPE, BLOOD_LOSS, BLOOD_TRANSFUSION, COMPLICATION, SURGEON, ASSISTANT, ANESTHESIOLOGIST, NURSE, FLUID_INFUSION',
    `entity_value`      VARCHAR(512)    DEFAULT NULL COMMENT '实体值',
    `entity_unit`       VARCHAR(32)     DEFAULT NULL COMMENT '单位(如: ml, mg)',
    `confidence`        DECIMAL(5,4)    DEFAULT NULL COMMENT '置信度 0.0000-1.0000',
    `source`            VARCHAR(32)     NOT NULL DEFAULT 'MODEL' COMMENT '来源: MODEL-模型识别, REGEX-正则匹配, RULE-规则引擎, MANUAL-人工修正',
    `start_pos`         INT             DEFAULT NULL COMMENT '在原文中起始位置',
    `end_pos`           INT             DEFAULT NULL COMMENT '在原文中结束位置',
    `original_text`     VARCHAR(255)    DEFAULT NULL COMMENT '原文片段',
    `verified`          TINYINT         DEFAULT 0 COMMENT '是否人工确认: 0-未确认, 1-已确认',
    `verified_by`       BIGINT          DEFAULT NULL COMMENT '确认人ID',
    `verified_time`     DATETIME        DEFAULT NULL COMMENT '确认时间',
    `remark`            VARCHAR(255)    DEFAULT NULL COMMENT '备注',
    `created_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_record_id` (`record_id`),
    KEY `idx_entity_type` (`entity_type`),
    KEY `idx_verified` (`verified`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实体抽取结果表';

-- -----------------------------------------------------------
-- 4. 病案首页表
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `medical_record_home`;
CREATE TABLE `medical_record_home` (
    `id`                  BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `record_id`           BIGINT          NOT NULL COMMENT '手术记录ID(关联surgery_record)',
    `patient_id`          VARCHAR(64)     DEFAULT NULL COMMENT '患者ID',
    `patient_name`        VARCHAR(64)     DEFAULT NULL COMMENT '患者姓名',
    `gender`              VARCHAR(8)      DEFAULT NULL COMMENT '性别',
    `age`                 INT             DEFAULT NULL COMMENT '年龄',
    `id_card_no`          VARCHAR(32)     DEFAULT NULL COMMENT '身份证号',
    `hospital_no`         VARCHAR(64)     DEFAULT NULL COMMENT '住院号',
    `admission_date`      DATE            DEFAULT NULL COMMENT '入院日期',
    `discharge_date`      DATE            DEFAULT NULL COMMENT '出院日期',
    `admission_days`      INT             DEFAULT NULL COMMENT '住院天数',
    `department`          VARCHAR(128)    DEFAULT NULL COMMENT '科室',
    `bed_no`              VARCHAR(32)     DEFAULT NULL COMMENT '床号',
    `admission_diagnosis` VARCHAR(255)    DEFAULT NULL COMMENT '入院诊断(ICD-10)',
    `discharge_diagnosis` VARCHAR(255)    DEFAULT NULL COMMENT '出院诊断(ICD-10)',
    `surgery_date`        DATETIME        DEFAULT NULL COMMENT '手术日期',
    `surgery_name`        VARCHAR(255)    DEFAULT NULL COMMENT '手术名称(ICD-9-CM-3)',
    `surgery_code`        VARCHAR(32)     DEFAULT NULL COMMENT '手术编码',
    `surgery_level`       VARCHAR(16)     DEFAULT NULL COMMENT '手术等级: 一级/二级/三级/四级',
    `incision_level`      VARCHAR(8)      DEFAULT NULL COMMENT '切口等级: Ⅰ/Ⅱ/Ⅲ',
    `incision_healing`    VARCHAR(8)      DEFAULT NULL COMMENT '切口愈合: 甲/乙/丙',
    `anesthesia_type`     VARCHAR(64)     DEFAULT NULL COMMENT '麻醉方式',
    `anesthesia_code`     VARCHAR(32)     DEFAULT NULL COMMENT '麻醉编码',
    `blood_loss`          DECIMAL(12,2)   DEFAULT NULL COMMENT '失血量(ml)',
    `blood_transfusion`   DECIMAL(12,2)   DEFAULT NULL COMMENT '输血量(ml)',
    `fluid_infusion`      DECIMAL(12,2)   DEFAULT NULL COMMENT '输液量(ml)',
    `complications`       TEXT            DEFAULT NULL COMMENT '术中并发症(JSON数组)',
    `surgeon`             VARCHAR(64)     DEFAULT NULL COMMENT '手术医生',
    `assistant_1`         VARCHAR(64)     DEFAULT NULL COMMENT '第一助手',
    `assistant_2`         VARCHAR(64)     DEFAULT NULL COMMENT '第二助手',
    `anesthesiologist`    VARCHAR(64)     DEFAULT NULL COMMENT '麻醉医生',
    `scrub_nurse`         VARCHAR(64)     DEFAULT NULL COMMENT '器械护士',
    `circulating_nurse`   VARCHAR(64)     DEFAULT NULL COMMENT '巡回护士',
    `critical_patient`    TINYINT         DEFAULT 0 COMMENT '是否危重患者: 0-否, 1-是',
    `hospitalization_fee` DECIMAL(14,2)   DEFAULT NULL COMMENT '住院费用(元)',
    `surgery_fee`         DECIMAL(14,2)   DEFAULT NULL COMMENT '手术费用(元)',
    `anesthesia_fee`      DECIMAL(14,2)   DEFAULT NULL COMMENT '麻醉费用(元)',
    `drug_fee`            DECIMAL(14,2)   DEFAULT NULL COMMENT '药品费用(元)',
    `exam_fee`            DECIMAL(14,2)   DEFAULT NULL COMMENT '检查费用(元)',
    `treatment_fee`       DECIMAL(14,2)   DEFAULT NULL COMMENT '治疗费用(元)',
    `bed_fee`             DECIMAL(14,2)   DEFAULT NULL COMMENT '床位费用(元)',
    `other_fee`           DECIMAL(14,2)   DEFAULT NULL COMMENT '其他费用(元)',
    `fill_user_id`        BIGINT          DEFAULT NULL COMMENT '填写人ID',
    `fill_user_name`      VARCHAR(64)     DEFAULT NULL COMMENT '填写人姓名',
    `fill_start_time`     DATETIME        DEFAULT NULL COMMENT '开始填写时间',
    `fill_end_time`       DATETIME        DEFAULT NULL COMMENT '结束填写时间',
    `fill_duration`       INT             DEFAULT NULL COMMENT '实际填写用时(秒)',
    `manual_duration_est` INT             DEFAULT 600 COMMENT '人工录入预估用时(秒)',
    `status`              VARCHAR(32)     NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT-草稿, PENDING-待审核, APPROVED-已审核, REJECTED-已驳回',
    `audit_user_id`       BIGINT          DEFAULT NULL COMMENT '审核人ID',
    `audit_user_name`     VARCHAR(64)     DEFAULT NULL COMMENT '审核人姓名',
    `audit_time`          DATETIME        DEFAULT NULL COMMENT '审核时间',
    `audit_remark`        VARCHAR(512)    DEFAULT NULL COMMENT '审核意见',
    `created_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_record_id` (`record_id`),
    KEY `idx_patient_id` (`patient_id`),
    KEY `idx_hospital_no` (`hospital_no`),
    KEY `idx_surgery_date` (`surgery_date`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='病案首页表';

-- -----------------------------------------------------------
-- 5. 字段映射配置表
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `field_mapping`;
CREATE TABLE `field_mapping` (
    `id`              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `entity_type`     VARCHAR(64)     NOT NULL COMMENT '实体类型',
    `target_table`    VARCHAR(64)     NOT NULL COMMENT '目标表名',
    `target_field`    VARCHAR(64)     NOT NULL COMMENT '目标字段名',
    `field_label`     VARCHAR(128)    DEFAULT NULL COMMENT '字段中文名(前端展示)',
    `required`        TINYINT         DEFAULT 0 COMMENT '是否必填: 0-否, 1-是',
    `data_type`       VARCHAR(32)     DEFAULT 'STRING' COMMENT '数据类型: STRING, INT, DECIMAL, DATE, DATETIME, ENUM',
    `unit`            VARCHAR(16)     DEFAULT NULL COMMENT '单位',
    `enum_values`     TEXT            DEFAULT NULL COMMENT '枚举值(JSON格式)',
    `sort_order`      INT             DEFAULT 0 COMMENT '排序',
    `enabled`         TINYINT         DEFAULT 1 COMMENT '是否启用: 0-禁用, 1-启用',
    `created_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_entity_target` (`entity_type`, `target_table`, `target_field`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字段映射配置表';

-- -----------------------------------------------------------
-- 6. 规则引擎配置表
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `extraction_rule`;
CREATE TABLE `extraction_rule` (
    `id`              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `rule_name`       VARCHAR(128)    NOT NULL COMMENT '规则名称',
    `rule_type`       VARCHAR(32)     NOT NULL COMMENT '规则类型: REGEX-正则, DICT-字典, RULE-逻辑规则',
    `entity_type`     VARCHAR(64)     NOT NULL COMMENT '目标实体类型',
    `pattern`         TEXT            DEFAULT NULL COMMENT '正则表达式/字典值',
    `rule_logic`      TEXT            DEFAULT NULL COMMENT '规则逻辑(JSON或脚本)',
    `priority`        INT             DEFAULT 0 COMMENT '优先级(值越大越先执行)',
    `enabled`         TINYINT         DEFAULT 1 COMMENT '是否启用: 0-禁用, 1-启用',
    `remark`          VARCHAR(512)    DEFAULT NULL COMMENT '规则说明',
    `created_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_entity_type` (`entity_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抽取规则配置表';

-- -----------------------------------------------------------
-- 7. 操作日志表
-- -----------------------------------------------------------
DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log` (
    `id`              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `module`          VARCHAR(64)     NOT NULL COMMENT '模块: RECORD_UPLOAD, OCR_PROCESS, NER_EXTRACT, HOME_PAGE_FILL, AUDIT, HIS_SYNC',
    `operation`       VARCHAR(64)     NOT NULL COMMENT '操作: CREATE, UPDATE, DELETE, SUBMIT, APPROVE, REJECT, SYNC',
    `record_id`       BIGINT          DEFAULT NULL COMMENT '关联记录ID',
    `user_id`         BIGINT          DEFAULT NULL COMMENT '操作人ID',
    `user_name`       VARCHAR(64)     DEFAULT NULL COMMENT '操作人姓名',
    `content`         TEXT            DEFAULT NULL COMMENT '操作内容(JSON)',
    `old_value`       TEXT            DEFAULT NULL COMMENT '修改前值(JSON)',
    `new_value`       TEXT            DEFAULT NULL COMMENT '修改后值(JSON)',
    `ip_address`      VARCHAR(64)     DEFAULT NULL COMMENT 'IP地址',
    `cost_time`       INT             DEFAULT 0 COMMENT '耗时(毫秒)',
    `created_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_record_id` (`record_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_created_time` (`created_time`),
    KEY `idx_module` (`module`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- ============================================================
-- 初始化数据
-- ============================================================

-- 初始化用户(密码: 123456, BCrypt加密: $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH)
INSERT INTO `sys_user` (`username`, `password`, `real_name`, `role`, `department`, `title`, `phone`) VALUES
('admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', 'ADMIN', '信息科', '高级工程师', '13800000000'),
('zhangyi', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '张医生', 'DOCTOR', '普外科', '主任医师', '13800000001'),
('lier', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '李医生', 'DOCTOR', '骨科', '副主任医师', '13800000002');

-- 初始化字段映射配置
INSERT INTO `field_mapping` (`entity_type`, `target_table`, `target_field`, `field_label`, `required`, `data_type`, `unit`, `enum_values`, `sort_order`, `enabled`) VALUES
('PATIENT_NAME', 'medical_record_home', 'patient_name', '患者姓名', 1, 'STRING', NULL, NULL, 1, 1),
('HOSPITAL_NO', 'medical_record_home', 'hospital_no', '住院号', 1, 'STRING', NULL, NULL, 2, 1),
('GENDER', 'medical_record_home', 'gender', '性别', 1, 'ENUM', NULL, '["男","女"]', 3, 1),
('AGE', 'medical_record_home', 'age', '年龄', 1, 'INT', '岁', NULL, 4, 1),
('SURGERY_DATE', 'medical_record_home', 'surgery_date', '手术日期', 1, 'DATETIME', NULL, NULL, 5, 1),
('SURGERY_NAME', 'medical_record_home', 'surgery_name', '手术名称', 1, 'STRING', NULL, NULL, 6, 1),
('SURGERY_LEVEL', 'medical_record_home', 'surgery_level', '手术等级', 0, 'ENUM', NULL, '["一级","二级","三级","四级"]', 7, 1),
('INCISION_LEVEL', 'medical_record_home', 'incision_level', '切口等级', 1, 'ENUM', NULL, '["Ⅰ","Ⅱ","Ⅲ"]', 8, 1),
('INCISION_HEALING', 'medical_record_home', 'incision_healing', '切口愈合', 0, 'ENUM', NULL, '["甲","乙","丙"]', 9, 1),
('ANESTHESIA_TYPE', 'medical_record_home', 'anesthesia_type', '麻醉方式', 1, 'STRING', NULL, NULL, 10, 1),
('BLOOD_LOSS', 'medical_record_home', 'blood_loss', '失血量', 0, 'DECIMAL', 'ml', NULL, 11, 1),
('BLOOD_TRANSFUSION', 'medical_record_home', 'blood_transfusion', '输血量', 0, 'DECIMAL', 'ml', NULL, 12, 1),
('FLUID_INFUSION', 'medical_record_home', 'fluid_infusion', '输液量', 0, 'DECIMAL', 'ml', NULL, 13, 1),
('COMPLICATION', 'medical_record_home', 'complications', '术中并发症', 0, 'STRING', NULL, NULL, 14, 1),
('SURGEON', 'medical_record_home', 'surgeon', '手术医生', 1, 'STRING', NULL, NULL, 15, 1),
('ASSISTANT', 'medical_record_home', 'assistant_1', '第一助手', 0, 'STRING', NULL, NULL, 16, 1),
('ANESTHESIOLOGIST', 'medical_record_home', 'anesthesiologist', '麻醉医生', 1, 'STRING', NULL, NULL, 17, 1),
('SCRUB_NURSE', 'medical_record_home', 'scrub_nurse', '器械护士', 0, 'STRING', NULL, NULL, 18, 1),
('CIRCULATING_NURSE', 'medical_record_home', 'circulating_nurse', '巡回护士', 0, 'STRING', NULL, NULL, 19, 1),
('DEPARTMENT', 'medical_record_home', 'department', '科室', 0, 'STRING', NULL, NULL, 20, 1);

-- 初始化正则抽取规则
INSERT INTO `extraction_rule` (`rule_name`, `rule_type`, `entity_type`, `pattern`, `priority`, `enabled`, `remark`) VALUES
('患者姓名-规则1', 'REGEX', 'PATIENT_NAME', '姓\s*名\s*[:：]\s*([\u4e00-\u9fa5]{2,6})', 100, 1, '匹配"姓名：张三"格式'),
('住院号-规则1', 'REGEX', 'HOSPITAL_NO', '住院号\s*[:：]\s*([A-Za-z0-9\-]{3,32})', 100, 1, '匹配"住院号：ZY20240101001"格式'),
('年龄-规则1', 'REGEX', 'AGE', '年\s*龄\s*[:：]\s*(\d{1,3})\s*岁', 100, 1, '匹配"年龄：56岁"格式'),
('手术日期-规则1', 'REGEX', 'SURGERY_DATE', '手术日期?\s*[:：]\s*(\d{4}[-\/\.]\d{1,2}[-\/\.]\d{1,2}\s*\d{0,2}[:：]?\d{0,2}[:：]?\d{0,2})', 100, 1, '匹配"手术日期：2024-01-15 09:30"格式'),
('手术日期-规则2', 'REGEX', 'SURGERY_DATE', '手术时间\s*[:：]\s*(\d{4}[-\/\.]\d{1,2}[-\/\.]\d{1,2}\s*\d{0,2}[:：]?\d{0,2}[:：]?\d{0,2})', 90, 1, '匹配"手术时间：2024年1月15日"格式'),
('手术名称-规则1', 'REGEX', 'SURGERY_NAME', '手术名称\s*[:：]\s*([\u4e00-\u9fa5A-Za-z0-9\-\+\(\)（）\s]{2,128})', 100, 1, '匹配"手术名称：腹腔镜阑尾切除术"格式'),
('切口等级-规则1', 'REGEX', 'INCISION_LEVEL', '切口[等級]级\s*[:：]?\s*([ⅠⅡⅢ一二III])\s*[类級级]', 100, 1, '匹配"切口等级：Ⅱ类"格式'),
('切口等级-规则2', 'REGEX', 'INCISION_LEVEL', '切口分类\s*[:：]?\s*([ⅠⅡⅢ一二III])\s*[类級级]?', 90, 1, '匹配"切口分类：Ⅰ"格式'),
('麻醉方式-规则1', 'REGEX', 'ANESTHESIA_TYPE', '麻醉方式\s*[:：]\s*([\u4e00-\u9fa5A-Za-z0-9\-\+\s]{2,64})', 100, 1, '匹配"麻醉方式：全身麻醉"格式'),
('失血量-规则1', 'REGEX', 'BLOOD_LOSS', '(?:失血量|出血量)\s*[:：]\s*(\d+(?:\.\d+)?)\s*ml', 100, 1, '匹配"失血量：200ml"格式'),
('失血量-规则2', 'REGEX', 'BLOOD_LOSS', '术中出血\s*[:：约]?\s*(\d+(?:\.\d+)?)\s*ml', 90, 1, '匹配"术中出血约300ml"格式'),
('输血量-规则1', 'REGEX', 'BLOOD_TRANSFUSION', '输血量\s*[:：约]?\s*(\d+(?:\.\d+)?)\s*ml', 100, 1, '匹配"输血量：400ml"格式'),
('输血量-规则2', 'REGEX', 'BLOOD_TRANSFUSION', '输血\s*[:：约]?\s*(\d+(?:\.\d+)?)\s*ml', 90, 1, '匹配"输血400ml"格式'),
('输液量-规则1', 'REGEX', 'FLUID_INFUSION', '输液量\s*[:：约]?\s*(\d+(?:\.\d+)?)\s*ml', 100, 1, '匹配"输液量：2000ml"格式'),
('输液量-规则2', 'REGEX', 'FLUID_INFUSION', '补液\s*[:：约]?\s*(\d+(?:\.\d+)?)\s*ml', 90, 1, '匹配"补液2500ml"格式'),
('手术医生-规则1', 'REGEX', 'SURGEON', '术者\s*[:：]\s*([\u4e00-\u9fa5]{2,6})', 100, 1, '匹配"术者：张医生"格式'),
('手术医生-规则2', 'REGEX', 'SURGEON', '主刀\s*[:：]\s*([\u4e00-\u9fa5]{2,6})', 90, 1, '匹配"主刀：李主任"格式'),
('助手-规则1', 'REGEX', 'ASSISTANT', '助手\s*[:：]\s*([\u4e00-\u9fa5\s、,]{2,64})', 100, 1, '匹配"助手：王医生、赵医生"格式'),
('麻醉医生-规则1', 'REGEX', 'ANESTHESIOLOGIST', '麻醉医师?\s*[:：]\s*([\u4e00-\u9fa5]{2,6})', 100, 1, '匹配"麻醉医师：陈医生"格式'),
('并发症-规则1', 'REGEX', 'COMPLICATION', '(?:并发症|术中意外|术中情况)\s*[:：]\s*([\u4e00-\u9fa5，,。；;\s]{2,256})', 100, 1, '匹配"并发症：无"格式');
