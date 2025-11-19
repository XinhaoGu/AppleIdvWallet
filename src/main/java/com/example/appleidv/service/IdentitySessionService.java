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
    private final String appleMediatorUrl;
    private final String relyingPartyId;

    public IdentitySessionService(
            @Value("${idv.apple.mediator:https://identity.apple.com/digital-credentials}") String appleMediatorUrl,
            @Value("${idv.relying-party-id:localhost}") String relyingPartyId) {
        this.appleMediatorUrl = appleMediatorUrl;
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
        List<DigitalCredentialRequestPayload.RequestedNamespace> namespaces = List.of(
                new DigitalCredentialRequestPayload.RequestedNamespace(
                        "org.iso.18013.5.1",
                        List.of("family_name", "given_name", "birth_date", "document_number")
                )
        );
        return new DigitalCredentialRequestPayload(
                "mdoc",
                "org.iso.18013.5.1.mDL",
                appleMediatorUrl,
                namespaces,
                session.getChallenge(),
                relyingPartyId,
                session.getId()
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

