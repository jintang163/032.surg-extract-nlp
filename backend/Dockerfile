# syntax=docker/dockerfile:1.6

FROM openjdk:17-slim AS builder
WORKDIR /app
ENV TZ=Asia/Shanghai
RUN apt-get update && apt-get install -y --no-install-recommends maven && rm -rf /var/lib/apt/lists/*

COPY pom.xml .
RUN mvn dependency:go-offline -B -q

COPY src ./src
RUN mvn clean package -DskipTests -B -q && \
    cp target/surg-extract-backend-*.jar app.jar

FROM openjdk:17-slim
WORKDIR /app
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms512m -Xmx1024m"

RUN apt-get update && apt-get install -y --no-install-recommends \
    tzdata \
    locales \
    fontconfig \
    fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/* \
    && ln -sf /usr/share/zoneinfo/${TZ} /etc/localtime \
    && echo ${TZ} > /etc/timezone

COPY --from=builder /app/app.jar app.jar

RUN mkdir -p /app/data/uploads /app/logs

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
