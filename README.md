# 手术记录结构化提取系统

基于NLP的手术记录结构化提取系统，自动填充病案首页，节省医生录入时间。

## 技术栈

- **前端**: React 18 + Ant Design 5 + TypeScript
- **后端主服务**: Spring Boot 3 + Java 17 + MyBatis-Plus
- **NLP微服务**: FastAPI + Python 3.10 + PyTorch
- **数据库**: MySQL 8.0 + Redis 7 + Elasticsearch 8
- **NLP模型**: BERT-BiLSTM-CRF（手术实体识别）
- **OCR**: Tesseract + PaddleOCR

## 项目结构

```
032.surg-extract-nlp/
├── backend/                    # Spring Boot后端主服务
│   ├── src/main/java/com/surg/extract/
│   │   ├── controller/         # REST API控制器
│   │   ├── service/            # 业务逻辑层
│   │   ├── mapper/             # 数据访问层
│   │   ├── entity/             # 数据库实体
│   │   ├── dto/                # 数据传输对象
│   │   ├── config/             # 配置类
│   │   ├── feign/              # NLP服务调用
│   │   └── SurgExtractApplication.java
│   └── pom.xml
├── nlp-service/               # FastAPI NLP微服务
│   ├── app/
│   │   ├── api/                # API路由
│   │   ├── models/             # NLP模型加载
│   │   ├── services/           # 业务服务（OCR、NER、规则）
│   │   ├── schemas/            # Pydantic数据模型
│   │   └── main.py
│   └── requirements.txt
├── frontend/                   # React前端
│   ├── src/
│   │   ├── components/         # 公共组件
│   │   ├── pages/              # 页面
│   │   ├── services/           # API服务
│   │   ├── store/              # 状态管理
│   │   └── utils/              # 工具函数
│   └── package.json
├── docker/                     # Docker编排
│   └── docker-compose.yml
└── sql/                        # 数据库脚本
    └── init.sql
```

## 核心功能模块

### 1. 文件上传与OCR预处理
- 支持纯文本、Word、PDF、图片格式
- 拖拽上传，自动识别文件类型
- OCR识别图片/PDF内容
- 文本噪声清洗（页眉页脚、表格线）
- 分句、分词、去停用词预处理

### 2. NLP实体识别与结构化提取
- BERT-BiLSTM-CRF模型抽取手术实体
- 正则表达式后处理增强
- 规则引擎校验与补全
- 支持的实体类型：
  - 患者基本信息：姓名、住院号、性别、年龄
  - 手术信息：手术日期、手术名称、切口等级、麻醉方式
  - 术中数据：出血量、输血量、输液量
  - 人员信息：手术医生、助手、麻醉医生、护士
  - 并发症：术中并发症、意外情况

### 3. 病案首页自动填充
- 结构化数据映射到病案首页字段
- 前端表单可视化编辑
- 下拉选择、单位校验、必填项校验
- 保存至MySQL并同步HIS系统
- 填写用时统计与提效对比
