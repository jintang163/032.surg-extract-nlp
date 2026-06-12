package com.surg.extract.common;

import lombok.Getter;

@Getter
public enum ErrorCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或Token已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),

    FILE_NOT_FOUND(1001, "文件不存在"),
    FILE_TYPE_NOT_SUPPORTED(1002, "不支持的文件类型"),
    FILE_SIZE_EXCEEDED(1003, "文件大小超限"),
    FILE_UPLOAD_ERROR(1004, "文件上传失败"),
    FILE_READ_ERROR(1005, "文件读取失败"),

    OCR_PROCESS_ERROR(2001, "OCR处理失败"),
    NER_PROCESS_ERROR(2002, "实体抽取失败"),
    NLP_SERVICE_UNAVAILABLE(2003, "NLP服务不可用"),

    RECORD_NOT_FOUND(3001, "手术记录不存在"),
    RECORD_STATUS_ERROR(3002, "手术记录状态错误"),
    HOME_PAGE_NOT_FOUND(3003, "病案首页不存在"),

    VALIDATION_ERROR(4001, "数据校验失败"),
    REQUIRED_FIELD_MISSING(4002, "必填字段缺失"),

    HIS_SYNC_ERROR(5001, "HIS同步失败"),

    TEMPLATE_NOT_FOUND(6001, "模板不存在"),
    TEMPLATE_CODE_DUPLICATE(6002, "模板编码已存在"),
    TEMPLATE_VERSION_NOT_FOUND(6003, "模板版本不存在"),

    PERMISSION_DENIED(4003, "权限不足，仅管理员可操作"),

    FIELD_NOT_FOUND(7001, "自定义字段不存在"),
    FIELD_CODE_DUPLICATE(7002, "字段编码已存在"),
    SAMPLE_NOT_FOUND(7003, "训练样本不存在"),
    NOT_ENOUGH_SAMPLES(7004, "训练样本不足，至少需要5个样本"),
    MODEL_TRAIN_FAILED(7005, "模型训练失败"),
    MODEL_NOT_FOUND(7006, "模型不存在或未训练"),

    SYSTEM_ERROR(9999, "系统内部错误");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
