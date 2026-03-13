# ===== Stage 1: Build the Spring Boot JAR =====
FROM eclipse-temurin:23-jdk AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml first (better layer caching)
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn

# Download dependencies (cached unless pom.xml changes)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Build the JAR (skip tests – they run separately)
RUN ./mvnw clean package -DskipTests -B

# ===== Stage 2: Runtime image =====
FROM eclipse-temurin:23-jre

WORKDIR /app

# Create directories for uploads and logs
RUN mkdir -p /app/uploads/attachments \
             /app/uploads/CADdrawings \
             /app/uploads/cutaway_images \
             /app/uploads/formulare \
             /app/uploads/images \
             /app/uploads/offers \
             /app/uploads/attachments/lieferanten \
             /app/logs

# Copy the built JAR from builder stage
COPY --from=builder /app/target/Kalkulationsprogramm-1.0.0.jar app.jar

# Expose server port
EXPOSE 8080

# Health check (simple TCP check since no actuator is present)
HEALTHCHECK --interval=30s --timeout=10s --retries=5 --start-period=60s \
    CMD curl -sf http://localhost:8080/ || exit 1

# Run with docker profile
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=docker"]
