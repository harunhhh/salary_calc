# ==========================================
# ステップ1：プログラムを組み立てる（重い作業）
# ==========================================
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# ==========================================
# ステップ2：完成品だけを「超軽量な箱」に移す
# ==========================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# ステップ1で作った完成品（.jar）だけをコピーしてくる
COPY --from=build /app/target/*.jar app.jar

# クラウドからのアクセスを受け付ける
EXPOSE 8080

# 開発用ツールは使わず、直接Javaを起動する！（超軽量）
CMD ["java", "-jar", "app.jar"]