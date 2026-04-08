# ==========================================
# ステップ1：プログラムを組み立てる
# ==========================================
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# ==========================================
# ステップ2：超軽量な箱に移して、本物を探し出して起動！
# ==========================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 組み立てた結果（targetフォルダ）を丸ごと持ってくる
COPY --from=build /app/target/ /app/target/

EXPOSE 8080

# ★最強の呪文：一番容量が大きい（＝本物の）jarファイルを自動で探して起動する！
CMD ["sh", "-c", "java -jar $(ls -S /app/target/*.jar | head -n 1)"]