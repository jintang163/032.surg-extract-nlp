#!/bin/bash

set -e

echo "========================================"
echo "  联邦学习多院区协同部署脚本"
echo "========================================"

SITE_ID="${1:-hospital-001}"
SITE_NAME="${2:-示例医院}"
AGGREGATOR_HOST="${3:-192.168.1.100}"
AGGREGATOR_PORT="${4:-8000}"

echo ""
echo "站点ID: ${SITE_ID}"
echo "站点名称: ${SITE_NAME}"
echo "聚合器地址: ${AGGREGATOR_HOST}:${AGGREGATOR_PORT}"
echo ""

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
FEDERATED_DIR="${PROJECT_DIR}/federated-deploy"
mkdir -p "${FEDERATED_DIR}/${SITE_ID}"

echo "[1/5] 创建站点配置文件..."
cat > "${FEDERATED_DIR}/${SITE_ID}/.env.federated" << EOF
# 联邦学习客户端配置
FEDERATED_ENABLED=true
FEDERATED_SITE_ID=${SITE_ID}
FEDERATED_SITE_NAME=${SITE_NAME}
FEDERATED_AGGREGATOR_HOST=${AGGREGATOR_HOST}
FEDERATED_AGGREGATOR_PORT=${AGGREGATOR_PORT}

# 差分隐私配置
FEDERATED_DP_ENABLED=true
FEDERATED_DP_NOISE_MULTIPLIER=1.0
FEDERATED_DP_MAX_GRAD_NORM=1.0

# 参数加密配置
FEDERATED_ENCRYPTION_ENABLED=true
FEDERATED_ENCRYPTION_KEY=your-encryption-key-here-change-this

# 训练配置
FEDERATED_LOCAL_EPOCHS=5
FEDERATED_BATCH_SIZE=16
FEDERATED_LEARNING_RATE=0.001
FEDERATED_MIN_SAMPLES=100
EOF

echo "配置文件已创建: ${FEDERATED_DIR}/${SITE_ID}/.env.federated"

echo ""
echo "[2/5] 创建联邦学习工作流脚本..."
cat > "${FEDERATED_DIR}/${SITE_ID}/federated-workflow.sh" << 'EOF'
#!/bin/bash
# 联邦学习工作流脚本
# 1. 本地增量训练
# 2. 导出加密参数
# 3. 上传到聚合器
# 4. 等待聚合
# 5. 下载全局参数
# 6. 导入本地模型

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/.env.federated"

echo "========================================"
echo "  联邦学习工作流 - ${FEDERATED_SITE_NAME}"
echo "========================================"

# 1. 本地增量训练
echo ""
echo "[1/6] 执行本地增量训练..."
cd "${SCRIPT_DIR}/../../nlp-service"

python scripts/incremental_finetune.py \
  --offline_mode true \
  --bert_local_path ./models/bert-base-chinese \
  --epochs ${FEDERATED_LOCAL_EPOCHS} \
  --batch_size ${FEDERATED_BATCH_SIZE} \
  --learning_rate ${FEDERATED_LEARNING_RATE} \
  --dp_enabled ${FEDERATED_DP_ENABLED} \
  --dp_noise_multiplier ${FEDERATED_DP_NOISE_MULTIPLIER} \
  --dp_max_grad_norm ${FEDERATED_DP_MAX_GRAD_NORM}

echo "本地训练完成"

# 2. 导出加密参数
echo ""
echo "[2/6] 导出模型参数（加密+差分隐私）..."
PARAM_FILE="${SCRIPT_DIR}/params_round_${ROUND_NUM:-0}.bin"

curl -X POST "http://localhost:8000/api/v1/federated/client/export-params" \
  -H "Content-Type: application/json" \
  -d '{
    "model_path": "./models/surgery-ner",
    "output_path": "'"${PARAM_FILE}"'",
    "train_sample_count": 500,
    "encryption_key": "'"${FEDERATED_ENCRYPTION_KEY}"'",
    "apply_dp": true,
    "round_num": '${ROUND_NUM:-0}'
  }'

echo "参数导出完成: ${PARAM_FILE}"

# 3. 上传到聚合器
echo ""
echo "[3/6] 上传参数到聚合器..."
PARAM_DATA=$(base64 -w 0 "${PARAM_FILE}")

curl -X POST "http://${FEDERATED_AGGREGATOR_HOST}:${FEDERATED_AGGREGATOR_PORT}/api/v1/federated/aggregator/receive" \
  -H "Content-Type: application/json" \
  -d '{
    "client_id": "'"${FEDERATED_SITE_ID}"'",
    "client_name": "'"${FEDERATED_SITE_NAME}"'",
    "round_num": '${ROUND_NUM:-0}',
    "param_data": "'"${PARAM_DATA}"'",
    "encryption_key": "'"${FEDERATED_ENCRYPTION_KEY}"'"
  }'

echo "参数上传完成"

# 4. 等待聚合
echo ""
echo "[4/6] 等待聚合完成..."
MAX_WAIT=3600
WAIT_INTERVAL=30
ELAPSED=0

while [ $ELAPSED -lt $MAX_WAIT ]; do
  STATUS=$(curl -s "http://${FEDERATED_AGGREGATOR_HOST}:${FEDERATED_AGGREGATOR_PORT}/api/v1/federated/aggregator/status" | python3 -c "import sys,json; print(json.load(sys.stdin).get('current_round', 0))")

  if [ "$STATUS" -gt "${ROUND_NUM:-0}" ]; then
    echo "聚合完成，当前轮次: ${STATUS}"
    break
  fi

  echo "等待中... 已等待 ${ELAPSED}s，当前轮次: ${STATUS}"
  sleep $WAIT_INTERVAL
  ELAPSED=$((ELAPSED + WAIT_INTERVAL))
done

# 5. 下载全局参数
echo ""
echo "[5/6] 下载全局模型参数..."
GLOBAL_PARAM_FILE="${SCRIPT_DIR}/global_params_round_${ROUND_NUM:-0}.bin"

curl -s "http://${FEDERATED_AGGREGATOR_HOST}:${FEDERATED_AGGREGATOR_PORT}/api/v1/federated/aggregator/result/${ROUND_NUM:-0}" \
  | python3 -c "
import sys, json, base64
data = json.load(sys.stdin)
if data.get('success') and data.get('result', {}).get('param_data'):
    param_data = data['result']['param_data']
    with open('${GLOBAL_PARAM_FILE}', 'wb') as f:
        f.write(base64.b64decode(param_data))
    print('下载成功')
else:
    print('下载失败:', data.get('error_message', '未知错误'))
    sys.exit(1)
"

# 6. 导入本地模型
echo ""
echo "[6/6] 导入全局模型参数到本地..."
curl -X POST "http://localhost:8000/api/v1/federated/client/import-params" \
  -H "Content-Type: application/json" \
  -d '{
    "param_file": "'"${GLOBAL_PARAM_FILE}"'",
    "model_path": "./models/surgery-ner",
    "output_model_path": "./models/surgery-ner-updated",
    "encryption_key": "'"${FEDERATED_ENCRYPTION_KEY}"'",
    "round_num": '${ROUND_NUM:-0}'
  }'

echo ""
echo "========================================"
echo "  联邦学习轮次 ${ROUND_NUM:-0} 完成！"
echo "========================================"
EOF

chmod +x "${FEDERATED_DIR}/${SITE_ID}/federated-workflow.sh"
echo "工作流脚本已创建: ${FEDERATED_DIR}/${SITE_ID}/federated-workflow.sh"

echo ""
echo "[3/5] 创建聚合器启动脚本..."
cat > "${FEDERATED_DIR}/start-aggregator.sh" << 'EOF'
#!/bin/bash
# 联邦学习聚合器启动脚本

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "========================================"
echo "  启动联邦学习聚合器"
echo "========================================"

cd "${SCRIPT_DIR}/../nlp-service"

export FEDERATED_MODE=aggregator
export OFFLINE_MODE=true

python -m uvicorn app.main:app \
  --host 0.0.0.0 \
  --port 8000 \
  --workers 2 \
  --log-level info

EOF

chmod +x "${FEDERATED_DIR}/start-aggregator.sh"
echo "聚合器启动脚本已创建: ${FEDERATED_DIR}/start-aggregator.sh"

echo ""
echo "[4/5] 创建客户端启动脚本..."
cat > "${FEDERATED_DIR}/${SITE_ID}/start-client.sh" << 'EOF'
#!/bin/bash
# 联邦学习客户端启动脚本

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/.env.federated"

echo "========================================"
echo "  启动联邦学习客户端 - ${FEDERATED_SITE_NAME}"
echo "========================================"

cd "${SCRIPT_DIR}/../../nlp-service"

export FEDERATED_MODE=client
export FEDERATED_SITE_ID=${FEDERATED_SITE_ID}
export FEDERATED_SITE_NAME=${FEDERATED_SITE_NAME}
export FEDERATED_DP_ENABLED=${FEDERATED_DP_ENABLED}
export FEDERATED_DP_NOISE_MULTIPLIER=${FEDERATED_DP_NOISE_MULTIPLIER}
export OFFLINE_MODE=true

python -m uvicorn app.main:app \
  --host 0.0.0.0 \
  --port 8000 \
  --workers 2 \
  --log-level info

EOF

chmod +x "${FEDERATED_DIR}/${SITE_ID}/start-client.sh"
echo "客户端启动脚本已创建: ${FEDERATED_DIR}/${SITE_ID}/start-client.sh"

echo ""
echo "[5/5] 创建联邦学习说明文档..."
cat > "${FEDERATED_DIR}/README.md" << 'EOF'
# 联邦学习多院区协同部署指南

## 概述

本指南用于在多个医院/院区之间部署联邦学习系统，实现在不共享原始医疗数据的前提下协同优化NLP模型。

## 架构说明

```
┌──────────────────┐     加密参数上传      ┌──────────────────┐
│   医院A (客户端)  │ ───────────────────► │                  │
└──────────────────┘                      │                  │
                                           │   联邦聚合器     │
┌──────────────────┐     加密参数上传      │   (可信第三方)    │
│   医院B (客户端)  │ ───────────────────► │                  │
└──────────────────┘                      │                  │
                                           │                  │
┌──────────────────┐     加密参数上传      │                  │
│   医院C (客户端)  │ ───────────────────► │                  │
└──────────────────┘                      └──────────────────┘
                                               │
                                               │  聚合后的全局参数
                                               ▼
                                        分发给所有客户端
```

## 部署步骤

### 第一步：部署联邦聚合器（仅需部署一次）

1. 选择一台可信的服务器作为聚合器（可以部署在其中一家医院，或独立的第三方机构）
2. 运行聚合器启动脚本：
```bash
cd federated-deploy
./start-aggregator.sh
```

3. 验证聚合器状态：
```bash
curl http://localhost:8000/api/v1/federated/aggregator/status
```

### 第二步：为每个医院部署客户端

1. 为每个医院生成配置：
```bash
# 医院1
./scripts/federated-deploy.sh hospital-001 "北京协和医院" 192.168.1.100 8000

# 医院2
./scripts/federated-deploy.sh hospital-002 "解放军总医院" 192.168.1.100 8000

# 医院3
./scripts/federated-deploy.sh hospital-003 "上海瑞金医院" 192.168.1.100 8000
```

2. 将生成的 `federated-deploy/<site-id>/` 目录复制到对应医院的服务器

3. 在每个医院服务器上启动客户端：
```bash
cd federated-deploy/hospital-001
./start-client.sh
```

4. 验证客户端状态：
```bash
curl http://localhost:8000/api/v1/federated/client/status
```

### 第三步：执行联邦学习训练

1. 在每个医院客户端上运行工作流脚本：
```bash
cd federated-deploy/hospital-001
ROUND_NUM=1 ./federated-workflow.sh
```

2. 工作流会自动执行以下步骤：
   - 本地增量训练（使用医院私有数据）
   - 应用差分隐私噪声
   - 加密模型参数
   - 上传到聚合器
   - 等待聚合完成
   - 下载全局参数
   - 更新本地模型

### 第四步：监控和审计

1. 查看聚合器状态：
```bash
curl http://<aggregator-host>:8000/api/v1/federated/aggregator/status
```

2. 查看隐私审计日志：
```bash
# 按时间范围查询
curl "http://localhost:8000/api/v1/privacy/audit/logs?event_type=FEDERATED_LEARNING&limit=100"

# 查看统计信息
curl http://localhost:8000/api/v1/privacy/audit/statistics
```

3. 查看差分隐私状态：
```bash
curl http://localhost:8000/api/v1/dp/status
```

## 隐私保护机制

### 1. 数据不出院
- 所有训练数据保存在各医院本地
- 只上传加密后的模型参数
- 原始数据永远不会离开医院内网

### 2. 差分隐私
- 训练过程中对梯度添加高斯噪声
- 可配置噪声强度（noise_multiplier）
- 隐私预算跟踪，防止过度消耗

### 3. 参数加密
- 使用Fernet对称加密算法
- 密钥由各医院自行管理
- 传输过程中参数始终加密

### 4. 选择性参数上传
- 只上传分类层参数，不上传BERT基础层
- 减少数据传输量和隐私泄露风险

### 5. 隐私审计
- 记录所有联邦学习操作
- 日志完整性校验（SHA256哈希）
- 支持操作溯源和审计

## 安全建议

### 网络安全
1. 聚合器和客户端之间使用VPN或专用网络连接
2. 配置防火墙，只允许可信IP访问聚合器
3. 启用TLS/SSL加密通信

### 密钥管理
1. 使用强加密密钥（至少32字节）
2. 定期更换加密密钥
3. 密钥与数据分开存储

### 访问控制
1. 联邦学习操作需要管理员权限
2. 记录所有操作日志
3. 定期审查访问记录

## 性能优化建议

### 模型训练
- 使用GPU加速本地训练
- 调整batch_size平衡速度和精度
- 根据数据量调整本地训练轮次

### 通信优化
- 使用参数压缩（zlib）减少传输体积
- 只上传有变化的参数层
- 设置合理的聚合频率（建议每周1-2次）

## 故障排查

### Q: 客户端无法连接聚合器
A: 1. 检查网络连通性（ping/telnet）
   2. 检查防火墙设置
   3. 确认聚合器地址和端口配置正确

### Q: 参数上传失败
A: 1. 检查加密密钥是否一致
   2. 检查参数文件是否损坏
   3. 查看服务日志获取详细错误信息

### Q: 聚合失败，客户端数量不足
A: 1. 检查各客户端是否已上传参数
   2. 调整min_clients参数
   3. 等待更多客户端完成训练

### Q: 模型精度下降明显
A: 1. 减小差分隐私噪声强度
   2. 增加本地训练轮次
   3. 增加参与训练的样本数量
   4. 调整聚合算法（尝试median或trimmed_mean）

## API接口列表

### 客户端接口
- `POST /api/v1/federated/client/export-params` - 导出模型参数
- `POST /api/v1/federated/client/import-params` - 导入模型参数
- `GET  /api/v1/federated/client/status` - 获取客户端状态

### 聚合器接口
- `POST /api/v1/federated/aggregator/receive` - 接收客户端参数
- `POST /api/v1/federated/aggregator/aggregate` - 执行参数聚合
- `GET  /api/v1/federated/aggregator/status` - 获取聚合器状态
- `GET  /api/v1/federated/aggregator/result/{round}` - 获取聚合结果

### 隐私保护接口
- `POST /api/v1/privacy/mask` - 数据脱敏
- `GET  /api/v1/privacy/audit/logs` - 查询审计日志
- `GET  /api/v1/privacy/audit/statistics` - 获取审计统计
- `GET  /api/v1/dp/status` - 获取差分隐私状态
- `POST /api/v1/dp/configure` - 配置差分隐私参数

EOF

echo "说明文档已创建: ${FEDERATED_DIR}/README.md"

echo ""
echo "========================================"
echo "  联邦学习部署准备完成！"
echo "========================================"
echo ""
echo "部署目录: ${FEDERATED_DIR}"
echo ""
echo "下一步操作:"
echo "1. 在聚合器服务器上运行: ${FEDERATED_DIR}/start-aggregator.sh"
echo "2. 在各医院服务器上运行: ${FEDERATED_DIR}/${SITE_ID}/start-client.sh"
echo "3. 执行联邦学习: ROUND_NUM=1 ${FEDERATED_DIR}/${SITE_ID}/federated-workflow.sh"
echo ""
echo "详细说明详见: ${FEDERATED_DIR}/README.md"
echo ""
