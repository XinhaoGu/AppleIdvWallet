# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
# Note: Using a base image that supports newer Java versions since this project targets Java 25.
# For now, falling back to 21 as 25 is very new, or we can find a specific nightly/EA build if strictly required.
# Assuming the project can run on 21 or we use a specific 25-ea image.
FROM eclipse-temurin:25-jre-alpine
# If 25 is not available in standard repos yet, we might need to use a specific tag or 21 if compatible.
# Updating to use 'latest' or specific tag if 25 fails, but trying 25 as per pom.xml
WORKDIR /app
COPY --from=build /app/target/apple-idv-wallet-0.0.1-SNAPSHOT.jar app.jar

# Create a non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

