@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ==============================================
echo   手术记录结构化提取系统 - Windows启动脚本
echo ==============================================

set ACTION=%1
if "%ACTION%"=="" set ACTION=help

if "%ACTION%"=="start" goto start_local
if "%ACTION%"=="docker-up" goto start_docker
if "%ACTION%"=="up" goto start_docker
if "%ACTION%"=="docker-down" goto stop_docker
if "%ACTION%"=="down" goto stop_docker
if "%ACTION%"=="build" goto build_all
if "%ACTION%"=="logs" goto show_logs
if "%ACTION%"=="help" goto show_help
goto show_help

:create_dirs
echo [1/6] 创建数据目录...
if not exist "data\mysql\data" mkdir data\mysql\data
if not exist "data\mysql\logs" mkdir data\mysql\logs
if not exist "data\redis\data" mkdir data\redis\data
if not exist "data\elasticsearch\data" mkdir data\elasticsearch\data
if not exist "data\uploads" mkdir data\uploads
if not exist "data\logs" mkdir data\logs
if not exist "nlp-service\config\dicts" mkdir nlp-service\config\dicts
if not exist "nlp-service\models" mkdir nlp-service\models
echo 目录创建完成
goto :eof

:check_env
echo [2/6] 检查运行环境...
where docker >nul 2>nul
if errorlevel 1 (
    echo 错误: 未安装 Docker Desktop，请先安装
    echo 下载地址: https://www.docker.com/products/docker-desktop
    exit /b 1
)
echo Docker 环境检查通过
goto :eof

:start_local
call :check_env
call :create_dirs

echo [3/6] 启动基础设施 (MySQL/Redis/ES)...
cd docker
docker compose up -d mysql redis elasticsearch
cd ..
echo 等待基础设施就绪 (约30秒)...
timeout /t 30 /nobreak >nul

echo [4/6] 启动后端服务...
cd backend
where mvn >nul 2>nul
if not errorlevel 1 (
    start "SpringBoot Backend" cmd /k "mvn spring-boot:run -Dspring-boot.run.profiles=dev"
    echo 后端服务已启动
) else (
    echo 警告: 未安装 Maven，请手动启动后端或使用 Docker 模式
)
cd ..
timeout /t 5 /nobreak >nul

echo [5/6] 启动 NLP 服务...
cd nlp-service
where python >nul 2>nul
if not errorlevel 1 (
    if not exist "venv" python -m venv venv
    call venv\Scripts\activate
    pip install -q -r requirements.txt
    start "NLP Service" cmd /k "uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload"
    echo NLP 服务已启动
) else (
    echo 警告: 未安装 Python，请手动启动 NLP 服务或使用 Docker 模式
)
cd ..
timeout /t 5 /nobreak >nul

echo [6/6] 启动前端...
cd frontend
where npm >nul 2>nul
if not errorlevel 1 (
    if not exist "node_modules" npm install
    start "Frontend Dev" cmd /k "npm run dev"
    echo 前端已启动
) else (
    echo 警告: 未安装 Node.js，请手动启动前端或使用 Docker 模式
)
cd ..

echo.
echo ==============================================
echo   服务启动完成
echo   前端: http://localhost:3000
echo   后端: http://localhost:8080/api
echo   API文档: http://localhost:8080/api/doc.html
echo   NLP服务: http://localhost:8000
echo   NLP文档: http://localhost:8000/docs
echo   测试账号: admin / 123456 (^管理员)
echo             zhangyi / 123456 (医生)
echo ==============================================
goto :eof

:start_docker
call :check_env
call :create_dirs
echo [3/6] 构建并启动所有服务 (Docker)...
cd docker
docker compose up -d --build
cd ..
echo [4/6] 等待服务就绪...
timeout /t 60 /nobreak >nul
echo [5/6] 检查服务健康状态...
echo [6/6] 启动完成！
echo.
echo ==============================================
echo   所有服务已通过 Docker Compose 启动
echo   前端: http://localhost:3000
echo   后端: http://localhost:8080/api
echo   API文档: http://localhost:8080/api/doc.html
echo   NLP服务: http://localhost:8000
echo   NLP文档: http://localhost:8000/docs
echo   测试账号: admin / 123456 (管理员)
echo             zhangyi / 123456 (医生)
echo ==============================================
goto :eof

:stop_docker
echo 停止所有 Docker 服务...
cd docker
docker compose down
cd ..
echo 服务已停止
goto :eof

:build_all
call :check_env
echo [3/6] 构建镜像...
cd docker
docker compose build
cd ..
echo 镜像构建完成
goto :eof

:show_logs
set SERVICE=%2
if "%SERVICE%"=="" set SERVICE=all
cd docker
if "%SERVICE%"=="all" (
    docker compose logs -f --tail=200
) else (
    docker compose logs -f --tail=200 %SERVICE%
)
cd ..
goto :eof

:show_help
echo.
echo 用法: start.bat ^<命令^> [参数]
echo.
echo 命令:
echo   start          本地开发模式启动 (推荐开发调试)
echo   docker-up      Docker Compose 模式启动
echo   docker-down    Docker Compose 停止服务
echo   build          仅构建镜像
echo   logs [服务]    查看服务日志 (backend/nlp-service/mysql/redis)
echo   help           显示帮助信息
echo.
echo 服务列表:
echo   mysql          MySQL 8.0 数据库
echo   redis          Redis 7 缓存
echo   elasticsearch  Elasticsearch 8 搜索引擎
echo   nlp-service    FastAPI NLP 微服务
echo   backend        Spring Boot 后端服务
echo   frontend       React 前端应用
echo.
echo 示例:
echo   start.bat start            :: 本地开发模式
echo   start.bat docker-up        :: Docker 一键启动
echo   start.bat logs backend     :: 查看后端日志
echo.
goto :eof
