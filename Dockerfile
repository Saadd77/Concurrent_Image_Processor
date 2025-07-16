# Use OpenJDK 17 as base image
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle .
COPY settings.gradle .

# Make gradlew executable
RUN chmod +x ./gradlew

# Copy source code
COPY src/ src/

# Create directories for input and output images
RUN mkdir -p input_images output_images

# Build the application
RUN ./gradlew build -x test

# Expose any ports if needed (adjust based on your application)
# EXPOSE 8080

# Set the entry point to run the application
CMD ["./gradlew", "run"]

# Alternative entry point if you want to run the JAR directly:
# CMD ["java", "-jar", "build/libs/ConcurrentImageProcessor-*.jar"]