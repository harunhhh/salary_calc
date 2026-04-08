# ==========================================
# ステップ1：プログラムを組み立てる
# ==========================================
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . .
# ★修正ポイント：組み立てた直後に、邪魔な「ダミー（plain.jar）」を完全に削除する！
RUN mvn clean package -DskipTests && rm -f target/*-plain.jar

# ==========================================
# ステップ2：本物の完成品だけを超軽量な箱に移す
# ==========================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
CMD ["java", "-jar", "app.jar"]