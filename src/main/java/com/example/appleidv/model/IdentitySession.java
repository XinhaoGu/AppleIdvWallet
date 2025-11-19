package com.example.appleidv.model;

import java.time.Instant;
import java.util.Objects;

public class IdentitySession {

    private final String id;
    private final String challenge;
    private final Instant createdAt;
    private final String relyingPartyId;
    private final String qrContent;

    private volatile IdentitySessionStatus status = IdentitySessionStatus.PENDING;
    private volatile Boolean validGovernmentId;
    private volatile String walletPayload;

    public IdentitySession(String id,
                           String challenge,
                           Instant createdAt,
                           String relyingPartyId,
                           String qrContent) {
        this.id = id;
        this.challenge = challenge;
        this.createdAt = createdAt;
        this.relyingPartyId = relyingPartyId;
        this.qrContent = qrContent;
    }

    public String getId() {
        return id;
    }

    public String getChallenge() {
        return challenge;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getRelyingPartyId() {
        return relyingPartyId;
    }

    public String getQrContent() {
       return qrContent;
    }

    public IdentitySessionStatus getStatus() {
        return status;
    }

    public Boolean getValidGovernmentId() {
        return validGovernmentId;
    }

    public String getWalletPayload() {
        return walletPayload;
    }

    public void markCompleted(boolean validWalletId, String payload) {
        this.status = validWalletId ? IdentitySessionStatus.SUCCESS : IdentitySessionStatus.FAILURE;
        this.validGovernmentId = validWalletId;
        this.walletPayload = payload;
    }

    public IdentitySessionSnapshot snapshot() {
        return new IdentitySessionSnapshot(
                id,
                status,
                validGovernmentId,
                createdAt,
                qrContent,
                relyingPartyId
        );
    }

    public record IdentitySessionSnapshot(
            String sessionId,
            IdentitySessionStatus status,
            Boolean validGovernmentId,
            Instant createdAt,
            String qrContent,
            String relyingPartyId
    ) {
        public boolean isTerminal() {
            return status == IdentitySessionStatus.SUCCESS || status == IdentitySessionStatus.FAILURE;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IdentitySession that = (IdentitySession) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

