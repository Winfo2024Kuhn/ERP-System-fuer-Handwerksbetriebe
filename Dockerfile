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
# Bauen und das Ergebnis auf einen festen Namen legen. Ohne das muesste hier
# die Projektversion stehen — die laeuft erfahrungsgemaess auseinander
# (pom stand auf 1.0.3, das Dockerfile noch auf 1.0.0).
RUN ./mvnw clean package -DskipTests -B \
 && cp target/Kalkulationsprogramm-*.jar /app/app.jar

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
COPY --from=builder /app/app.jar app.jar

# Expose server port
EXPOSE 8080

# Health check. wget statt curl: das JRE-Image bringt kein curl mit, der
# Healthcheck konnte deshalb nie gruen werden und der Container blieb
# dauerhaft auf 'unhealthy', obwohl die App lief.
HEALTHCHECK --interval=30s --timeout=10s --retries=5 --start-period=60s \
    CMD wget -qO- http://localhost:8080/ >/dev/null 2>&1 || exit 1

# Run with docker profile
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=docker"]
