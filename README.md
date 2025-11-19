# Apple IDV Wallet PoC

Spring Boot 3.5.7 + Java 25 reference implementation that showcases how to request mobile IDs (mdocs) from Apple Wallet using the W3C Digital Credentials API.

## Features

- Spring Boot web UI with a `Check Apple IDV` button.
- Automatic Safari + iPhone detection. If supported, Safari opens Apple Wallet directly via `navigator.identity.get({ digital: … })`.
- QR-code fallback for desktop/unsupported browsers so users can hand off the flow to an iPhone.
- REST callbacks that record whether a valid government ID (driver license or passport) was shared from Wallet.
- Configurable mediator/relying party identifiers and short-lived verification sessions with signed challenges.

## Getting started

```bash
cd AppleIdvWallet
mvn spring-boot:run
```

Browse to <http://localhost:8080>.

## Testing the wallet flow

1. From an iPhone running iOS 17+ open Safari and navigate to your workstation URL (or scan the QR code shown when you click the button from a desktop browser).
2. Tap **Check Apple IDV**. Safari invokes the Digital Credentials API, which launches the Apple Wallet app.
3. Choose a government-issued ID (State Driver's License, Passport, etc.). Apple Wallet returns to Safari and the page calls `POST /api/idv/session/{id}/result` with `true`.
4. If Wallet does not return a document (user cancels or has no valid ID), the page reports `false`.

> ℹ️ Apple limits the Digital Credentials API to secure contexts served over HTTPS with a publicly trusted certificate. When testing locally, use an HTTPS reverse proxy such as ngrok or Cloudflare Tunnel and update `idv.relying-party-id`.

## Important files

- `pom.xml` – Spring Boot 3.5.7 project definition targeting Java 25.
- `src/main/java/com/example/appleidv/service/IdentitySessionService.java` – creates verification sessions, challenges, QR payloads, and records Wallet callbacks.
- `src/main/java/com/example/appleidv/controller/IdentityVerificationController.java` – REST endpoints for session lifecycle, QR rendering, and result reporting.
- `src/main/resources/templates/index.html` & `static/js/idv.js` – UI + Digital Credentials API client.

## Configuration

`src/main/resources/application.yml` exposes:

```yaml
idv:
  relying-party-id: apple-idv-wallet.local
  apple:
    mediator: https://identity.apple.com/digital-credentials
```

Override these (for example via environment variables) when deploying behind your production hostname or when Apple changes their mediator endpoint.

## Caveats & next steps

- This demo stores sessions in-memory; use a shared persistence layer for multi-instance deployments.
- The Digital Credentials API is only available in WebKit on iOS 17.4+/macOS 14.4+. Guard feature detection before calling.
- Always host the site over HTTPS; an unsigned origin cannot talk to Apple Wallet.


