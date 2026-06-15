#!/bin/bash

set -e

echo "========================================"
echo "  手术记录结构化提取系统 - 离线部署准备脚本"
echo "========================================"

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OFFLINE_DIR="${PROJECT_DIR}/offline-deploy"
MODELS_DIR="${OFFLINE_DIR}/models"
PACKAGES_DIR="${OFFLINE_DIR}/packages"
DOCKER_DIR="${OFFLINE_DIR}/docker"

echo ""
echo "[1/6] 创建离线部署目录结构..."
mkdir -p "${MODELS_DIR}/bert-base-chinese"
mkdir -p "${MODELS_DIR}/surgery-ner"
mkdir -p "${PACKAGES_DIR}/pip"
mkdir -p "${PACKAGES_DIR}/npm"
mkdir -p "${DOCKER_DIR}"
mkdir -p "${OFFLINE_DIR}/scripts"
mkdir -p "${OFFLINE_DIR}/config"
mkdir -p "${OFFLINE_DIR}/data"

echo "目录结构已创建: ${OFFLINE_DIR}"

echo ""
echo "[2/6] 下载Python依赖包（用于离线安装）..."
cd "${PROJECT_DIR}/nlp-service"
if [ -f "requirements.txt" ]; then
    pip download -r requirements.txt -d "${PACKAGES_DIR}/pip" --no-binary :none:
    echo "Python依赖包已下载到: ${PACKAGES_DIR}/pip"
else
    echo "警告: 未找到 requirements.txt，跳过Python依赖下载"
fi

echo ""
echo "[3/6] 下载前端npm依赖包（用于离线构建）..."
cd "${PROJECT_DIR}/frontend"
if [ -f "package.json" ]; then
    npm install
    npm pack --pack-destination "${PACKAGES_DIR}/npm"
    echo "npm依赖包已下载到: ${PACKAGES_DIR}/npm"
else
    echo "警告: 未找到 package.json，跳过npm依赖下载"
fi

echo ""
echo "[4/6] 下载BERT模型（用于本地加载）..."
cd "${MODELS_DIR}/bert-base-chinese"

if command -v python3 &> /dev/null; then
    python3 << 'EOF'
from huggingface_hub import snapshot_download
try:
    snapshot_download(
        "bert-base-chinese",
        local_dir=".",
        local_files_only=False,
        cache_dir="./cache"
    )
    print("BERT模型下载成功")
except Exception as e:
    print(f"警告: BERT模型下载失败: {e}")
    print("请手动从 https://huggingface.co/bert-base-chinese 下载")
EOF
else
    echo "警告: 未找到python3，请手动下载BERT模型"
fi

echo ""
echo "[5/6] 复制现有配置文件和脚本..."
cp "${PROJECT_DIR}/docker/.env.offline" "${OFFLINE_DIR}/config/.env"
cp "${PROJECT_DIR}/docker/docker-compose-offline.yml" "${OFFLINE_DIR}/docker-compose.yml"
cp "${PROJECT_DIR}/frontend/.env.offline" "${OFFLINE_DIR}/config/frontend.env"
cp "${PROJECT_DIR}/frontend/nginx-offline.conf" "${OFFLINE_DIR}/config/nginx.conf"
cp "${PROJECT_DIR}/backend/src/main/resources/application-offline.yml" "${OFFLINE_DIR}/config/application.yml"

echo "配置文件已复制到: ${OFFLINE_DIR}/config"

echo ""
echo "[6/6] 创建部署说明文件..."
cat > "${OFFLINE_DIR}/README.md" << 'EOF'
# 手术记录结构化提取系统 - 离线部署包

## 目录结构

```
offline-deploy/
├── models/                    # 模型文件目录
│   ├── bert-base-chinese/    # BERT中文预训练模型
│   └── surgery-ner/           # 手术NER模型
├── packages/                  # 离线依赖包
│   ├── pip/                   # Python依赖包
│   └── npm/                   # Node.js依赖包
├── docker/                    # Docker镜像
├── config/                    # 配置文件
│   ├── .env                   # Docker环境变量
│   ├── application.yml        # 后端配置
│   ├── frontend.env           # 前端环境变量
│   └── nginx.conf             # Nginx配置
├── scripts/                   # 部署脚本
├── data/                      # 数据目录
└── docker-compose.yml         # Docker编排文件
```

## 部署步骤

### 1. 环境准备
- 安装 Docker 20.10+ 和 Docker Compose 2.0+
- 安装 NVIDIA Container Toolkit（GPU版本需要）
- 确保服务器未连接公网，所有依赖已准备完毕

### 2. 加载Docker镜像
```bash
# 加载所有镜像
docker load -i docker/backend-image.tar
docker load -i docker/frontend-image.tar
docker load -i docker/nlp-service-image.tar
docker load -i docker/mysql-image.tar
docker load -i docker/elasticsearch-image.tar
docker load -i docker/neo4j-image.tar
docker load -i docker/redis-image.tar
```

### 3. 修改配置文件
根据实际环境修改 `config/` 目录下的配置文件：
- `.env`: 数据库密码、服务端口等
- `application.yml`: 数据库连接、隐私保护配置
- `frontend.env`: API地址配置

### 4. 启动服务
```bash
# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看服务日志
docker-compose logs -f
```

### 5. 验证部署
```bash
# 检查前端页面
curl http://localhost:8080

# 检查后端健康状态
curl http://localhost:8080/api/health

# 检查NLP服务健康状态
curl http://localhost:8000/api/v1/health
```

## 隐私保护配置

### 数据脱敏
在 `application.yml` 中配置：
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
```

### 差分隐私
```yaml
federated:
  client:
    differential-privacy:
      enabled: true
      noise-multiplier: 1.0
      max-gradient-norm: 1.0
```

### 联邦学习
```yaml
federated:
  enabled: true
  client:
    site-id: hospital-001
    site-name: "XXX医院"
```

## 常见问题

### Q: 服务启动失败，提示找不到模型文件
A: 确保 `models/bert-base-chinese` 目录下有完整的BERT模型文件

### Q: GPU版本无法使用CUDA
A: 1. 检查NVIDIA驱动是否正确安装
   2. 检查nvidia-container-toolkit是否正确配置
   3. 运行 `nvidia-smi` 验证GPU可用性

### Q: 数据脱敏不生效
A: 检查 `privacy.data-masking.enabled` 是否设置为 true

### Q: 如何查看隐私审计日志
A: 访问 `/api/v1/privacy/audit/logs` 接口查询审计日志

## 安全加固建议

1. 修改所有默认密码
2. 配置防火墙，只开放必要端口
3. 启用HTTPS，配置SSL证书
4. 定期备份数据和审计日志
5. 限制API访问IP范围
6. 定期更新系统和依赖包

EOF

echo ""
echo "========================================"
echo "  离线部署准备完成！"
echo "========================================"
echo ""
echo "请将以下目录复制到离线服务器:"
echo "  ${OFFLINE_DIR}"
echo ""
echo "部署说明详见: ${OFFLINE_DIR}/README.md"
echo ""
