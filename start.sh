#!/bin/bash

set -e

echo "=============================================="
echo "  手术记录结构化提取系统 - 本地启动脚本"
echo "=============================================="

ACTION=${1:-help}
ENV=${2:-dev}

create_dirs() {
    echo "[1/6] 创建数据目录..."
    mkdir -p \
        data/mysql/data \
        data/mysql/logs \
        data/redis/data \
        data/elasticsearch/data \
        data/uploads \
        data/logs \
        nlp-service/config/dicts \
        nlp-service/models
    echo "目录创建完成"
}

check_env() {
    echo "[2/6] 检查运行环境..."

    if ! command -v docker &> /dev/null; then
        echo "错误: 未安装 Docker，请先安装 Docker Desktop"
        exit 1
    fi

    if ! command -v docker compose &> /dev/null && ! command -v docker-compose &> /dev/null; then
        echo "错误: 未安装 Docker Compose"
        exit 1
    fi

    echo "Docker 环境检查通过"
}

build_all() {
    echo "[3/6] 构建镜像..."
    cd docker
    if command -v docker compose &> /dev/null; then
        docker compose build
    else
        docker-compose build
    fi
    echo "镜像构建完成"
    cd ..
}

start_backend() {
    echo "启动 Spring Boot 后端..."
    if command -v mvn &> /dev/null; then
        cd backend
        mvn spring-boot:run -Dspring-boot.run.profiles=dev &
        BACKEND_PID=$!
        echo "后端已启动，PID: $BACKEND_PID"
        cd ..
    else
        echo "警告: 未安装 Maven，请手动启动后端服务或使用 Docker 模式"
    fi
}

start_nlp() {
    echo "启动 NLP 微服务..."
    if command -v python &> /dev/null; then
        cd nlp-service
        if [ ! -d "venv" ]; then
            python -m venv venv
        fi
        if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "win32" ]]; then
            source venv/Scripts/activate
        else
            source venv/bin/activate
        fi
        pip install -q -r requirements.txt
        python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload &
        NLP_PID=$!
        echo "NLP 服务已启动，PID: $NLP_PID"
        cd ..
    else
        echo "警告: 未安装 Python，请手动启动 NLP 服务或使用 Docker 模式"
    fi
}

start_frontend() {
    echo "启动前端开发服务器..."
    if command -v npm &> /dev/null; then
        cd frontend
        if [ ! -d "node_modules" ]; then
            npm install
        fi
        npm run dev &
        FRONTEND_PID=$!
        echo "前端已启动，PID: $FRONTEND_PID"
        cd ..
    else
        echo "警告: 未安装 Node.js，请手动启动前端或使用 Docker 模式"
    fi
}

start_local() {
    check_env
    create_dirs
    echo "[3/6] 启动基础设施 (MySQL/Redis/ES)..."
    cd docker
    if command -v docker compose &> /dev/null; then
        docker compose up -d mysql redis elasticsearch
    else
        docker-compose up -d mysql redis elasticsearch
    fi
    cd ..
    echo "等待基础设施就绪..."
    sleep 30
    echo "[4/6] 启动后端服务..."
    start_backend
    sleep 5
    echo "[5/6] 启动 NLP 服务..."
    start_nlp
    sleep 5
    echo "[6/6] 启动前端服务..."
    start_frontend

    echo ""
    echo "=============================================="
    echo "  服务启动完成"
    echo "  前端: http://localhost:3000"
    echo "  后端: http://localhost:8080/api"
    echo "  API文档: http://localhost:8080/api/doc.html"
    echo "  NLP服务: http://localhost:8000"
    echo "  NLP文档: http://localhost:8000/docs"
    echo "  测试账号: admin / 123456 (管理员)"
    echo "            zhangyi / 123456 (医生)"
    echo "=============================================="
    echo ""
    echo "按 Ctrl+C 停止所有服务"
    wait
}

start_docker() {
    check_env
    create_dirs
    echo "[3/6] 构建并启动所有服务 (Docker)..."
    cd docker
    if command -v docker compose &> /dev/null; then
        docker compose up -d --build
    else
        docker-compose up -d --build
    fi
    cd ..
    echo "[4/6] 等待服务就绪..."
    sleep 60
    echo "[5/6] 检查服务健康状态..."
    echo "[6/6] 启动完成！"

    echo ""
    echo "=============================================="
    echo "  所有服务已通过 Docker Compose 启动"
    echo "  前端: http://localhost:3000"
    echo "  后端: http://localhost:8080/api"
    echo "  API文档: http://localhost:8080/api/doc.html"
    echo "  NLP服务: http://localhost:8000"
    echo "  NLP文档: http://localhost:8000/docs"
    echo "  测试账号: admin / 123456 (管理员)"
    echo "            zhangyi / 123456 (医生)"
    echo "=============================================="
}

stop_docker() {
    echo "停止所有 Docker 服务..."
    cd docker
    if command -v docker compose &> /dev/null; then
        docker compose down
    else
        docker-compose down
    fi
    cd ..
    echo "服务已停止"
}

show_logs() {
    SERVICE=${2:-all}
    cd docker
    if command -v docker compose &> /dev/null; then
        if [ "$SERVICE" == "all" ]; then
            docker compose logs -f --tail=200
        else
            docker compose logs -f --tail=200 "$SERVICE"
        fi
    else
        if [ "$SERVICE" == "all" ]; then
            docker-compose logs -f --tail=200
        else
            docker-compose logs -f --tail=200 "$SERVICE"
        fi
    fi
    cd ..
}

show_help() {
    echo ""
    echo "用法: $0 <命令> [参数]"
    echo ""
    echo "命令:"
    echo "  start          本地开发模式启动 (推荐开发调试)"
    echo "  docker-up      Docker Compose 模式启动"
    echo "  docker-down    Docker Compose 停止服务"
    echo "  build          仅构建镜像"
    echo "  logs [服务]    查看服务日志 (backend/nlp-service/mysql/redis)"
    echo "  help           显示帮助信息"
    echo ""
    echo "服务列表:"
    echo "  mysql          MySQL 8.0 数据库"
    echo "  redis          Redis 7 缓存"
    echo "  elasticsearch  Elasticsearch 8 搜索引擎"
    echo "  nlp-service    FastAPI NLP 微服务"
    echo "  backend        Spring Boot 后端服务"
    echo "  frontend       React 前端应用"
    echo ""
    echo "示例:"
    echo "  $0 start            # 本地开发模式"
    echo "  $0 docker-up        # Docker 一键启动"
    echo "  $0 logs backend     # 查看后端日志"
    echo ""
}

case $ACTION in
    start)
        start_local
        ;;
    docker-up|up)
        start_docker
        ;;
    docker-down|down)
        stop_docker
        ;;
    build)
        check_env
        build_all
        ;;
    logs)
        show_logs
        ;;
    help|*)
        show_help
        ;;
esac
