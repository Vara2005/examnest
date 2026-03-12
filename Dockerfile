FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY . .

RUN chmod +x mvnw && ./mvnw clean package -DskipTests

CMD ["java","-jar","target/examnest-0.0.1-SNAPSHOT.jar"]