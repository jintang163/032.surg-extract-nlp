@echo off
chcp 65001 >nul

echo ========================================
echo   手术记录结构化提取系统 - 离线部署准备脚本
echo ========================================

set "PROJECT_DIR=%~dp0.."
set "OFFLINE_DIR=%PROJECT_DIR%\offline-deploy"
set "MODELS_DIR=%OFFLINE_DIR%\models"
set "PACKAGES_DIR=%OFFLINE_DIR%\packages"
set "DOCKER_DIR=%OFFLINE_DIR%\docker"

echo.
echo [1/6] 创建离线部署目录结构...
if not exist "%MODELS_DIR%\bert-base-chinese" mkdir "%MODELS_DIR%\bert-base-chinese"
if not exist "%MODELS_DIR%\surgery-ner" mkdir "%MODELS_DIR%\surgery-ner"
if not exist "%PACKAGES_DIR%\pip" mkdir "%PACKAGES_DIR%\pip"
if not exist "%PACKAGES_DIR%\npm" mkdir "%PACKAGES_DIR%\npm"
if not exist "%DOCKER_DIR%" mkdir "%DOCKER_DIR%"
if not exist "%OFFLINE_DIR%\scripts" mkdir "%OFFLINE_DIR%\scripts"
if not exist "%OFFLINE_DIR%\config" mkdir "%OFFLINE_DIR%\config"
if not exist "%OFFLINE_DIR%\data" mkdir "%OFFLINE_DIR%\data"

echo 目录结构已创建: %OFFLINE_DIR%

echo.
echo [2/6] 下载Python依赖包（用于离线安装）...
cd /d "%PROJECT_DIR%\nlp-service"
if exist "requirements.txt" (
    pip download -r requirements.txt -d "%PACKAGES_DIR%\pip"
    echo Python依赖包已下载到: %PACKAGES_DIR%\pip
) else (
    echo 警告: 未找到 requirements.txt，跳过Python依赖下载
)

echo.
echo [3/6] 下载前端npm依赖包（用于离线构建）...
cd /d "%PROJECT_DIR%\frontend"
if exist "package.json" (
    call npm install
    call npm pack --pack-destination "%PACKAGES_DIR%\npm"
    echo npm依赖包已下载到: %PACKAGES_DIR%\npm
) else (
    echo 警告: 未找到 package.json，跳过npm依赖下载
)

echo.
echo [4/6] 下载BERT模型（用于本地加载）...
cd /d "%MODELS_DIR%\bert-base-chinese"

where python >nul 2>&1
if %errorlevel%==0 (
    python -c "from huggingface_hub import snapshot_download; snapshot_download('bert-base-chinese', local_dir='.', local_files_only=False, cache_dir='./cache')" 2>nul
    if %errorlevel%==0 (
        echo BERT模型下载成功
    ) else (
        echo 警告: BERT模型下载失败，请手动从 https://huggingface.co/bert-base-chinese 下载
    )
) else (
    echo 警告: 未找到Python，请手动下载BERT模型
)

echo.
echo [5/6] 复制现有配置文件和脚本...
copy "%PROJECT_DIR%\docker\.env.offline" "%OFFLINE_DIR%\config\.env" >nul
copy "%PROJECT_DIR%\docker\docker-compose-offline.yml" "%OFFLINE_DIR%\docker-compose.yml" >nul
copy "%PROJECT_DIR%\frontend\.env.offline" "%OFFLINE_DIR%\config\frontend.env" >nul
copy "%PROJECT_DIR%\frontend\nginx-offline.conf" "%OFFLINE_DIR%\config\nginx.conf" >nul
copy "%PROJECT_DIR%\backend\src\main\resources\application-offline.yml" "%OFFLINE_DIR%\config\application.yml" >nul

echo 配置文件已复制到: %OFFLINE_DIR%\config

echo.
echo [6/6] 创建部署说明文件...
(
echo # 手术记录结构化提取系统 - 离线部署包
echo.
echo ## 目录结构
echo.
echo ```
echo offline-deploy/
echo ├── models/                    # 模型文件目录
echo │   ├── bert-base-chinese/    # BERT中文预训练模型
echo │   └── surgery-ner/           # 手术NER模型
echo ├── packages/                  # 离线依赖包
echo │   ├── pip/                   # Python依赖包
echo │   └── npm/                   # Node.js依赖包
echo ├── docker/                    # Docker镜像
echo ├── config/                    # 配置文件
echo │   ├── .env                   # Docker环境变量
echo │   ├── application.yml        # 后端配置
echo │   ├── frontend.env           # 前端环境变量
echo │   └── nginx.conf             # Nginx配置
echo ├── scripts/                   # 部署脚本
echo ├── data/                      # 数据目录
echo └── docker-compose.yml         # Docker编排文件
echo ```
echo.
echo ## 部署步骤
echo.
echo ### 1. 环境准备
echo - 安装 Docker 20.10+ 和 Docker Compose 2.0+
echo - 安装 NVIDIA Container Toolkit（GPU版本需要）
echo - 确保服务器未连接公网，所有依赖已准备完毕
echo.
echo ### 2. 加载Docker镜像
echo ```bash
echo # 加载所有镜像
echo docker load -i docker/backend-image.tar
echo docker load -i docker/frontend-image.tar
echo docker load -i docker/nlp-service-image.tar
echo docker load -i docker/mysql-image.tar
echo docker load -i docker/elasticsearch-image.tar
echo docker load -i docker/neo4j-image.tar
echo docker load -i docker/redis-image.tar
echo ```
echo.
echo ### 3. 修改配置文件
echo 根据实际环境修改 `config/` 目录下的配置文件：
echo - `.env`: 数据库密码、服务端口等
echo - `application.yml`: 数据库连接、隐私保护配置
echo - `frontend.env`: API地址配置
echo.
echo ### 4. 启动服务
echo ```bash
echo # 启动所有服务
echo docker-compose up -d
echo.
echo # 查看服务状态
echo docker-compose ps
echo.
echo # 查看服务日志
echo docker-compose logs -f
echo ```
echo.
echo ### 5. 验证部署
echo ```bash
echo # 检查前端页面
echo curl http://localhost:8080
echo.
echo # 检查后端健康状态
echo curl http://localhost:8080/api/health
echo.
echo # 检查NLP服务健康状态
echo curl http://localhost:8000/api/v1/health
echo ```
echo.
echo ## 隐私保护配置
echo.
echo ### 数据脱敏
echo 在 `application.yml` 中配置：
echo ```yaml
echo privacy:
echo   enabled: true
echo   data-masking:
echo     enabled: true
echo     fields:
echo       patient-name: true
echo       patient-id: true
echo       id-card: true
echo       phone: true
echo       address: true
echo ```
echo.
echo ### 差分隐私
echo ```yaml
echo federated:
echo   client:
echo     differential-privacy:
echo       enabled: true
echo       noise-multiplier: 1.0
echo       max-gradient-norm: 1.0
echo ```
echo.
echo ### 联邦学习
echo ```yaml
echo federated:
echo   enabled: true
echo   client:
echo     site-id: hospital-001
echo     site-name: "XXX医院"
echo ```
echo.
echo ## 常见问题
echo.
echo ### Q: 服务启动失败，提示找不到模型文件
echo A: 确保 `models/bert-base-chinese` 目录下有完整的BERT模型文件
echo.
echo ### Q: GPU版本无法使用CUDA
echo A: 1. 检查NVIDIA驱动是否正确安装
echo    2. 检查nvidia-container-toolkit是否正确配置
echo    3. 运行 `nvidia-smi` 验证GPU可用性
echo.
echo ### Q: 数据脱敏不生效
echo A: 检查 `privacy.data-masking.enabled` 是否设置为 true
echo.
echo ### Q: 如何查看隐私审计日志
echo A: 访问 `/api/v1/privacy/audit/logs` 接口查询审计日志
echo.
echo ## 安全加固建议
echo.
echo 1. 修改所有默认密码
echo 2. 配置防火墙，只开放必要端口
echo 3. 启用HTTPS，配置SSL证书
echo 4. 定期备份数据和审计日志
echo 5. 限制API访问IP范围
echo 6. 定期更新系统和依赖包
) > "%OFFLINE_DIR%\README.md"

echo.
echo ========================================
echo   离线部署准备完成！
echo ========================================
echo.
echo 请将以下目录复制到离线服务器:
echo   %OFFLINE_DIR%
echo.
echo 部署说明详见: %OFFLINE_DIR%\README.md
echo.
pause
