# ===== Stage 1: Frontends bauen (PC-Oberflaeche + Zeiterfassung) =====
# Die Build-Ausgabe liegt zwar auch versioniert unter src/main/resources/static,
# kann dort aber hinter dem Quellcode zurueckhaengen. Das Image fuer die
# Kundenserver baut sie deshalb immer frisch aus dem Quellcode.
FROM node:24-bookworm-slim AS frontend

WORKDIR /build

# Erst nur die Paketlisten -> npm ci wird gecacht, solange sie sich nicht aendern
COPY react-pc-frontend/package.json react-pc-frontend/package-lock.json react-pc-frontend/
COPY react-zeiterfassung/package.json react-zeiterfassung/package-lock.json react-zeiterfassung/
RUN npm ci --prefix react-pc-frontend --no-audit --no-fund \
 && npm ci --prefix react-zeiterfassung --no-audit --no-fund

COPY react-pc-frontend react-pc-frontend
COPY react-zeiterfassung react-zeiterfassung

# Beide Builds schreiben nach ../src/main/resources/static (siehe vite.config.ts)
RUN npm run build --prefix react-pc-frontend \
 && npm run build --prefix react-zeiterfassung

# ===== Stage 2: Build the Spring Boot JAR =====
FROM eclipse-temurin:23-jdk AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml first (better layer caching)
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn

# Download dependencies (cached unless pom.xml changes)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Versionierte Frontend-Ausgabe komplett durch den frischen Build ersetzen.
# Gefahrlos: alles unter static/ stammt aus den beiden Frontend-Builds
# (inkl. public/-Dateien wie Logos und Icons).
RUN rm -rf src/main/resources/static
COPY --from=frontend /build/src/main/resources/static src/main/resources/static

# Build the JAR (skip tests – they run separately)
# Bauen und das Ergebnis auf einen festen Namen legen. Ohne das muesste hier
# die Projektversion stehen — die laeuft erfahrungsgemaess auseinander
# (pom stand auf 1.0.3, das Dockerfile noch auf 1.0.0).
RUN ./mvnw clean package -DskipTests -B \
 && cp target/Kalkulationsprogramm-*.jar /app/app.jar

# ===== Stage 3: Runtime image =====
FROM eclipse-temurin:23-jre

# Git-Commit, aus dem das Image gebaut wurde. Setzt die GitHub Action; das
# Nachtupdate nennt ihn in seiner Handy-Nachricht ("von abc1234 auf def5678").
ARG GIT_COMMIT=unbekannt
LABEL org.opencontainers.image.revision="${GIT_COMMIT}" \
      org.opencontainers.image.title="ERP Handwerk"

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
# /actuator/health meldet erst 200, wenn Datenbank + Flyway-Migrationen durch sind.
HEALTHCHECK --interval=30s --timeout=10s --retries=5 --start-period=120s \
    CMD wget -qO- http://localhost:8080/actuator/health >/dev/null 2>&1 || exit 1

# Profil per Umgebungsvariable (nicht als Startargument): so kann
# docker-compose es ueberschreiben, z. B. SPRING_PROFILES_ACTIVE=docker,postgres
# fuer Kunden-Installationen mit PostgreSQL. Ein --spring.profiles.active-Argument
# haette Vorrang vor der Umgebung und wuerde das verhindern.
ENV SPRING_PROFILES_ACTIVE=docker
ENTRYPOINT ["java", "-jar", "app.jar"]
