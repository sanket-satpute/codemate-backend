# ==========================
# Stage 1: Build the application securely
# ==========================
FROM maven:3.9.6-eclipse-temurin-17 AS builder

# Set the working directory
WORKDIR /app

# Copy exclusively the pom.xml and download dependencies to drastically speed up Docker caching
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the actual API source code
COPY src ./src

# Package the application natively without tests to build the JAR
RUN mvn clean package -DskipTests

# ==========================
# Stage 2: Create a minimal production image
# ==========================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Only copy the compiled jar file from the builder phase, abandoning the heavy maven engine and source code
COPY --from=builder /app/target/backend-0.0.1-SNAPSHOT.jar ./app.jar

# Expose the API port
EXPOSE 8080

# Execute the application
ENTRYPOINT ["java", "-jar", "app.jar"]
