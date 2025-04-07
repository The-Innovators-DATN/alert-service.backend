# Stage 1: Build app using Maven
FROM maven:3.9.4-eclipse-temurin-21 AS builder

# Set working directory
WORKDIR /build

# Copy source code
COPY . .

# Build project
RUN mvn clean package -DskipTests

# Stage 2: Run app using JDK
FROM eclipse-temurin:21-jdk-jammy

# App directory
WORKDIR /app

# Copy JAR from builder stage
COPY --from=builder /build/target/alert-0.0.1-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 3001

# Run the JAR
ENTRYPOINT ["java", "-jar", "app.jar"]
