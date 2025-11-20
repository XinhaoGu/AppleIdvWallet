# Deployment Guide

## Prerequisites
- Docker installed (if using containerization)
- Java 25 SDK (if running locally)
- A public domain name (e.g., `idv.example.com`)
- An SSL Certificate (HTTPS is **mandatory** for the Digital Credentials API)

## Option 1: Docker (Recommended)

This ensures you have the correct Java environment without installing it on your host.

### 1. Build the Image
```bash
docker build -t apple-idv-wallet .
```

### 2. Run the Container
Replace `your-domain.com` with your actual public domain.

```bash
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e IDV_DOMAIN=your-domain.com \
  apple-idv-wallet
```

### 3. Expose via HTTPS
The application runs on port 8080. You need a reverse proxy to handle HTTPS.

#### Using NGINX (Example)
```nginx
server {
    listen 443 ssl;
    server_name your-domain.com;
    
    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-Proto https;
    }
}
```

## Option 2: Standard JAR

### 1. Build
```bash
./mvnw clean package -DskipTests
```

### 2. Run
```bash
export IDV_DOMAIN=your-domain.com
java -Dspring.profiles.active=prod -jar target/apple-idv-wallet-0.0.1-SNAPSHOT.jar
```

## Important: Apple Wallet Verification
For the integration to work in production:
1. Your site **MUST** be served over HTTPS.
2. Your `relying-party-id` (configured via `IDV_DOMAIN`) must match the domain serving the page.
3. If you are using Apple Business Connect or a specific implementation, you might need to host a `.well-known/apple-app-site-association` file, though this demo uses the open web API which is more flexible.

