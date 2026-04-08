# Java 17とMavenが入った「専用の箱」を用意する
FROM maven:3.8.5-openjdk-17

# 箱の中の作業ディレクトリを決める
WORKDIR /app

# あなたのパソコンのコードを、箱の中に全部コピーする
COPY . .

# プログラムを本番用に組み立てる
RUN mvn clean package -DskipTests

# クラウドからのアクセスを受け付ける
EXPOSE 8080

# サーバーを起動する！
CMD ["mvn", "spring-boot:run"]