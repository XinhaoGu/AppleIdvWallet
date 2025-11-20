package com.example.appleidv.service;

import com.example.appleidv.model.DigitalCredentialRequestPayload;
import com.example.appleidv.model.IdentityResultRequest;
import com.example.appleidv.model.IdentitySession;
import com.example.appleidv.model.IdentitySessionResponse;
import com.example.appleidv.model.IdentitySessionStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IdentitySessionService {

    private static final Duration SESSION_TTL = Duration.ofMinutes(15);

    private final Map<String, IdentitySession> sessions = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final String relyingPartyId;

    public IdentitySessionService(
            @Value("${idv.relying-party-id:localhost}") String relyingPartyId) {
        this.relyingPartyId = relyingPartyId;
    }

    public IdentitySessionResponse startSession(String originUrl) {
        purgeExpiredSessions();
        String sessionId = UUID.randomUUID().toString();
        String challenge = generateChallenge();
        String qrContent = originUrl + (originUrl.contains("?") ? "&" : "?") + "session=" + sessionId;
        IdentitySession session = new IdentitySession(
                sessionId,
                challenge,
                Instant.now(),
                relyingPartyId,
                qrContent
        );
        sessions.put(sessionId, session);

        DigitalCredentialRequestPayload payload = buildRequestPayload(session);
        return new IdentitySessionResponse(sessionId, qrContent, payload);
    }

    public Optional<IdentitySession.IdentitySessionSnapshot> findSession(String sessionId) {
        purgeExpiredSessions();
        return Optional.ofNullable(sessions.get(sessionId))
                .map(IdentitySession::snapshot);
    }

    public IdentitySessionResponse resumeSession(String sessionId) {
        purgeExpiredSessions();
        IdentitySession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Unknown session id: " + sessionId);
        }
        DigitalCredentialRequestPayload payload = buildRequestPayload(session);
        return new IdentitySessionResponse(sessionId, session.getQrContent(), payload);
    }

    public IdentitySessionStatus reportResult(String sessionId, IdentityResultRequest resultRequest) {
        IdentitySession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Unknown session id: " + sessionId);
        }
        session.markCompleted(
                Boolean.TRUE.equals(resultRequest.hasValidId()),
                resultRequest.walletResponse() == null ? null : resultRequest.walletResponse().toString()
        );
        return session.getStatus();
    }

    private DigitalCredentialRequestPayload buildRequestPayload(IdentitySession session) {
        // OpenID4VP parameters
        // Based on WWDC24/25: "Verify with Wallet on the Web"
        // The request object inside the provider maps to the OpenID4VP Authorization Request.
        
        Map<String, Object> presentationDefinition = Map.of(
            "id", "mDL-request-demo",
            "input_descriptors", List.of(
                Map.of(
                    "id", "org.iso.18013.5.1.mDL",
                    "format", Map.of(
                        "mso_mdoc", Map.of(
                            "alg", List.of("ES256", "ES384", "ES512")
                        )
                    ),
                    "constraints", Map.of(
                        "limit_disclosure", "required",
                        "fields", List.of(
                            Map.of(
                                "path", List.of("$['org.iso.18013.5.1']['family_name']"),
                                "intent_to_retain", false
                            ),
                            Map.of(
                                "path", List.of("$['org.iso.18013.5.1']['given_name']"),
                                "intent_to_retain", false
                            ),
                             Map.of(
                                "path", List.of("$['org.iso.18013.5.1']['birth_date']"),
                                "intent_to_retain", false
                            ),
                             Map.of(
                                "path", List.of("$['org.iso.18013.5.1']['document_number']"),
                                "intent_to_retain", false
                            )
                        )
                    )
                )
            )
        );

        Map<String, Object> openId4VpRequest = Map.of(
            "client_id", relyingPartyId,
            "client_id_scheme", "web-origin",
            "response_type", "vp_token",
            "response_mode", "web_message",
            "nonce", session.getChallenge(),
            "presentation_definition", presentationDefinition
        );

        return new DigitalCredentialRequestPayload(
            "openid4vp",
            openId4VpRequest
        );
    }

    private String generateChallenge() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void purgeExpiredSessions() {
        Instant cutoff = Instant.now().minus(SESSION_TTL);
        sessions.values().removeIf(session -> session.getCreatedAt().isBefore(cutoff));
    }
}
