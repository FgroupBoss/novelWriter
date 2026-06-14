# 使用本地 Maven 打包产物，避免构建时拉取 maven 镜像
FROM eclipse-temurin:8-jre
ENV TZ=Asia/Shanghai
WORKDIR /app
COPY target/novel-writer-backend-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "-jar", "app.jar"]
