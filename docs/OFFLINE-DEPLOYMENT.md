# 手术记录结构化提取系统 - 离线部署与隐私计算方案

## 一、方案概述

### 1.1 背景与目标

针对军队/保密医院的特殊安全需求，本方案提供纯离线部署版本，确保：
- **数据不出院区**：所有患者数据、医疗记录均在医院内部处理
- **模型本地部署**：NLP模型完全运行在医院GPU服务器上
- **无公网依赖**：前端界面和后端服务不依赖任何外部网络资源
- **隐私保护**：通过差分隐私、联邦学习等技术保护数据隐私
- **多院区协同**：在不共享原始数据的前提下实现多医院协同优化模型

### 1.2 适用场景

- 军队医院、部队医院
- 保密单位附属医院
- 对数据安全有极高要求的医疗机构
- 需要跨院区协同但无法共享数据的医疗集团

### 1.3 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                      医院内网 (完全离线)                     │
│                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │   前端界面    │    │  后端服务     │    │  NLP服务     │  │
│  │  (离线React) │    │  (SpringBoot)│    │  (GPU加速)   │  │
│  │  Nginx代理   │◄──►│  隐私保护     │◄──►│  联邦学习    │  │
│  │  无外部依赖  │    │  数据脱敏     │    │  差分隐私    │  │
│  └──────────────┘    └──────┬───────┘    └──────┬───────┘  │
│                             │                     │          │
│                             ▼                     ▼          │
│                       ┌──────────────┐    ┌──────────────┐  │
│                       │  数据库      │    │  本地模型     │  │
│                       │  (MySQL)     │    │  (BERT+NER)   │  │
│                       │  审计日志     │    │  加密存储     │  │
│                       └──────┬───────┘    └──────────────┘  │
│                              │                               │
│                              ▼                               │
│                       ┌──────────────┐                       │
│                       │  联邦学习     │                       │
│                       │  客户端       │                       │
│                       │  参数加密上传  │                       │
│                       └──────┬───────┘                       │
└──────────────────────────────┼───────────────────────────────┘
                               │ 加密参数（非原始数据）
                               ▼
                    ┌───────────────────────┐
                    │   联邦学习聚合器       │
                    │   (可信第三方/专网)    │
                    │   FedAvg/Median算法   │
                    └───────────────────────┘
```

## 二、核心特性

### 2.1 纯离线部署

| 特性 | 说明 |
|------|------|
| 无公网依赖 | 所有依赖本地部署，不访问任何外部网络 |
| 本地模型加载 | BERT模型本地存储，不从HuggingFace下载 |
| 前端资源本地化 | 所有JS、CSS、字体、图标均本地打包 |
| 内部网络通信 | 所有服务通过内网IP通信 |

### 2.2 隐私保护技术

#### 2.2.1 数据脱敏

支持对以下敏感字段进行脱敏处理：
- **患者姓名**：保留首字，其余用*替代（如：张*、李**）
- **住院号/病历号**：完全脱敏为***
- **身份证号**：保留首尾2位，中间脱敏（如：11**********11）
- **手机号**：保留前3后2位（如：138****89）
- **地址/邮箱**：完全脱敏
- **NLP识别实体**：对识别出的敏感实体自动脱敏

#### 2.2.2 差分隐私

| 技术 | 说明 |
|------|------|
| 梯度裁剪 | 限制单个样本对模型的最大影响 |
| 高斯噪声 | 向梯度添加噪声，模糊个体贡献 |
| 隐私预算 | 跟踪隐私消耗，防止过度泄露 |
| RDP会计 | 使用Renyi差分隐私精确计算隐私消耗 |

#### 2.2.3 联邦学习

| 特性 | 说明 |
|------|------|
| 本地训练 | 各医院使用私有数据独立训练 |
| 参数聚合 | 仅上传模型参数，不上传原始数据 |
| 加密传输 | 参数使用Fernet加密后传输 |
| 多种聚合算法 | FedAvg、Median、TrimmedMean |
| 抗异常值 | Median/TrimmedMean算法抵抗恶意客户端 |

### 2.3 GPU加速

- **CUDA 11.8 + cuDNN 8**：最新GPU加速框架
- **自动混合精度**：FP16/FP32混合训练，提速2-3倍
- **显存优化**：设置显存使用比例，避免OOM
- **模型预热**：消除首次推理延迟
- **批量推理**：优化高并发场景下的吞吐量

## 三、部署指南

### 3.1 硬件要求

#### 最小配置（CPU版本）
- CPU：8核16线程以上
- 内存：32GB以上
- 磁盘：500GB SSD以上
- 网络：千兆内网

#### 推荐配置（GPU版本）
- CPU：16核32线程以上
- 内存：64GB以上
- GPU：NVIDIA RTX 3090/4090 或 A10/A30/A100
- 显存：24GB以上
- 磁盘：1TB NVMe SSD以上
- 网络：万兆内网

### 3.2 软件要求

- 操作系统：CentOS 7.9+ / Ubuntu 20.04+ / Windows Server 2019+
- Docker：20.10.0+
- Docker Compose：2.0.0+
- NVIDIA Driver：525.60.11+（GPU版本需要）
- NVIDIA Container Toolkit：1.13.0+（GPU版本需要）

### 3.3 快速部署

#### 步骤1：准备离线部署包

在有网络的机器上运行准备脚本：

```bash
# Linux/Mac
chmod +x scripts/prepare-offline.sh
./scripts/prepare-offline.sh

# Windows
scripts\prepare-offline.bat
```

脚本会自动完成：
1. 创建离线部署目录结构
2. 下载Python依赖包
3. 下载npm依赖包
4. 下载BERT模型
5. 复制配置文件
6. 生成部署说明

#### 步骤2：准备Docker镜像

```bash
# 构建镜像
cd docker
docker-compose -f docker-compose-offline.yml build

# 保存镜像
docker save -o offline-deploy/docker/backend-image.tar surg-extract-backend:offline
docker save -o offline-deploy/docker/frontend-image.tar surg-extract-frontend:offline
docker save -o offline-deploy/docker/nlp-service-image.tar surg-extract-nlp:gpu-offline
docker save -o offline-deploy/docker/mysql-image.tar mysql:8.0
docker save -o offline-deploy/docker/elasticsearch-image.tar elasticsearch:8.11.1
docker save -o offline-deploy/docker/neo4j-image.tar neo4j:5.13-community
docker save -o offline-deploy/docker/redis-image.tar redis:7.2
```

#### 步骤3：复制到离线服务器

将整个 `offline-deploy` 目录复制到离线服务器：

```bash
# 使用U盘或移动硬盘复制
# 或使用scp通过内网传输
scp -r offline-deploy user@offline-server:/opt/
```

#### 步骤4：在离线服务器上部署

```bash
cd /opt/offline-deploy

# 1. 加载Docker镜像
docker load -i docker/backend-image.tar
docker load -i docker/frontend-image.tar
docker load -i docker/nlp-service-image.tar
docker load -i docker/mysql-image.tar
docker load -i docker/elasticsearch-image.tar
docker load -i docker/neo4j-image.tar
docker load -i docker/redis-image.tar

# 2. 修改配置文件
vi config/.env           # 修改数据库密码、端口等
vi config/application.yml # 修改数据库连接、隐私配置

# 3. 初始化数据库
docker-compose up -d mysql
# 等待MySQL启动后执行初始化脚本
docker exec -i mysql mysql -uroot -p<password> < sql/init.sql

# 4. 启动所有服务
docker-compose up -d

# 5. 验证部署
curl http://localhost:8080/api/health
curl http://localhost:8000/api/v1/health
```

### 3.4 配置说明

#### 环境变量配置（config/.env）

```env
# 数据库配置
MYSQL_ROOT_PASSWORD=your_secure_password
MYSQL_DATABASE=surg_extract
MYSQL_USER=surg_extract
MYSQL_PASSWORD=your_db_password

# 服务端口配置
BACKEND_PORT=8080
FRONTEND_PORT=80
NLP_SERVICE_PORT=8000

# 隐私保护配置
PRIVACY_ENABLED=true
DATA_MASKING_ENABLED=true
FEDERATED_ENABLED=false  # 单医院部署设为false

# 联邦学习配置（如需多院区协同）
FEDERATED_SITE_ID=hospital-001
FEDERATED_SITE_NAME=XXX医院
FEDERATED_AGGREGATOR_HOST=192.168.1.100
```

#### 应用配置（config/application.yml）

```yaml
privacy:
  enabled: true
  data-masking:
    enabled: true
    fields:
      patient-name: true
      patient-id: true
      id-card: true
      phone: true
      address: true
      email: true

federated:
  enabled: false
  client:
    site-id: hospital-001
    site-name: "XXX医院"
    differential-privacy:
      enabled: true
      noise-multiplier: 1.0
      max-gradient-norm: 1.0
      target-epsilon: 1.0
```

## 四、联邦学习部署

### 4.1 部署架构

```
┌──────────────────┐        专网/VPN        ┌──────────────────┐
│  医院A (客户端)   │ ◄────────────────────► │                  │
│  - 本地训练       │                         │   联邦聚合器      │
│  - 差分隐私       │   加密参数（非原始数据） │   - 参数接收      │
│  - 参数加密上传    │ ◄────────────────────► │   - 参数聚合      │
└──────────────────┘                         │   - 结果分发      │
                                             └──────────────────┘
┌──────────────────┐        专网/VPN              ▲
│  医院B (客户端)   │ ◄───────────────────────────┘
│  - 本地训练       │
│  - 差分隐私       │
│  - 参数加密上传    │
└──────────────────┘
```

### 4.2 部署步骤

#### 1. 部署联邦聚合器

```bash
# 生成聚合器配置
./scripts/federated-deploy.sh aggregator "联邦聚合器" 0.0.0.0 8000

# 启动聚合器
cd federated-deploy
./start-aggregator.sh
```

#### 2. 部署各医院客户端

```bash
# 为医院1生成配置
./scripts/federated-deploy.sh hospital-001 "北京协和医院" 192.168.1.100 8000

# 为医院2生成配置
./scripts/federated-deploy.sh hospital-002 "解放军总医院" 192.168.1.100 8000

# 将配置复制到各医院服务器
# 在各医院启动客户端
cd federated-deploy/hospital-001
./start-client.sh
```

#### 3. 执行联邦学习

```bash
# 在各医院执行训练工作流
cd federated-deploy/hospital-001
ROUND_NUM=1 ./federated-workflow.sh
```

### 4.3 聚合算法说明

#### FedAvg（加权平均）
- **原理**：按各医院样本数加权平均参数
- **优点**：计算简单，收敛快
- **适用场景**：数据分布相似，无恶意客户端

#### Median（中位数聚合）
- **原理**：取各客户端参数的中位数
- **优点**：抵抗异常值和恶意客户端
- **适用场景**：存在不可信客户端

#### TrimmedMean（裁剪均值）
- **原理**：去除最大最小10%后取平均
- **优点**：兼顾鲁棒性和收敛速度
- **适用场景**：一般场景推荐

## 五、API接口说明

### 5.1 隐私保护接口

#### 数据脱敏
```http
POST /api/v1/privacy/mask
Content-Type: application/json

{
  "text": "患者张三，身份证号110101199001011234，电话13812345678",
  "fields": ["patient_name", "id_card", "phone"]
}
```

响应：
```json
{
  "success": true,
  "masked_text": "患者张*，身份证号11**********34，电话138****5678",
  "processing_time_ms": 5
}
```

#### 查询审计日志
```http
GET /api/v1/privacy/audit/logs?event_type=FEDERATED_LEARNING&limit=100
```

### 5.2 联邦学习接口

#### 客户端导出参数
```http
POST /api/v1/federated/client/export-params
Content-Type: application/json

{
  "model_path": "./models/surgery-ner",
  "train_sample_count": 500,
  "encryption_key": "your-encryption-key",
  "apply_dp": true,
  "round_num": 1
}
```

#### 聚合器执行聚合
```http
POST /api/v1/federated/aggregator/aggregate
Content-Type: application/json

{
  "round_num": 1,
  "algorithm": "fedavg",
  "min_clients": 2,
  "encryption_key": "your-encryption-key"
}
```

### 5.3 差分隐私接口

#### 获取DP状态
```http
GET /api/v1/dp/status
```

响应：
```json
{
  "success": true,
  "enabled": true,
  "noise_multiplier": 1.0,
  "max_grad_norm": 1.0,
  "target_epsilon": 1.0,
  "spent_epsilon": 0.5,
  "remaining_epsilon": 9.5,
  "max_epsilon": 10.0
}
```

## 六、安全加固

### 6.1 网络安全

1. **防火墙配置**
   - 只开放必要端口（80, 8080, 8000）
   - 限制访问IP范围
   - 禁用公网访问

2. **通信加密**
   - 启用HTTPS，配置SSL证书
   - 内部服务间通信使用TLS
   - 联邦学习使用专网/VPN连接

### 6.2 数据安全

1. **数据库加密**
   - 启用MySQL透明数据加密（TDE）
   - 敏感字段存储加密
   - 定期备份并加密存储

2. **模型安全**
   - 模型文件加密存储
   - 完整性校验（SHA256哈希）
   - 访问权限控制

### 6.3 访问控制

1. **身份认证**
   - 强密码策略
   - 双因素认证（2FA）
   - 定期更换密码

2. **权限管理**
   - 最小权限原则
   - 基于角色的访问控制（RBAC）
   - 定期审计权限分配

### 6.4 审计监控

1. **日志管理**
   - 所有操作记录审计日志
   - 日志加密存储，防止篡改
   - 定期备份日志

2. **安全监控**
   - 实时监控异常访问
   - 告警机制
   - 定期安全审计

## 七、常见问题

### 7.1 部署问题

**Q: 服务启动失败，提示找不到BERT模型**
A: 确保 `models/bert-base-chinese` 目录下包含以下文件：
   - config.json
   - pytorch_model.bin
   - vocab.txt
   - tokenizer_config.json

**Q: GPU版本无法使用CUDA**
A: 1. 检查NVIDIA驱动：`nvidia-smi`
   2. 检查nvidia-container-toolkit是否正确安装
   3. 确保Docker runtime设置为nvidia

**Q: 前端页面无法访问**
A: 1. 检查Nginx容器状态：`docker-compose ps nginx`
   2. 检查防火墙是否开放80端口
   3. 查看Nginx日志：`docker-compose logs nginx`

### 7.2 隐私保护问题

**Q: 数据脱敏不生效**
A: 1. 检查 `privacy.data-masking.enabled` 是否为true
   2. 确认需要脱敏的字段已启用
   3. 查看服务日志确认脱敏逻辑执行

**Q: 如何验证差分隐私生效**
A: 1. 调用 `/api/v1/dp/status` 查看隐私消耗
   2. 检查训练日志中是否有DP相关输出
   3. 对比有无DP时的模型参数差异

**Q: 联邦学习参数上传失败**
A: 1. 检查加密密钥是否一致
   2. 确认网络连通性
   3. 查看聚合器和客户端日志

### 7.3 性能问题

**Q: 推理速度慢**
A: 1. 使用GPU版本
   2. 启用模型预热
   3. 调整批量大小
   4. 启用自动混合精度

**Q: 训练收敛慢**
A: 1. 调整学习率
   2. 增加训练轮次
   3. 检查数据质量
   4. 考虑减小差分隐私噪声强度

## 八、联系方式

如有技术问题，请联系：
- 技术支持：support@surg-extract.com
- 安全咨询：security@surg-extract.com

---

**文档版本**：v1.0.0
**最后更新**：2024-01-01
