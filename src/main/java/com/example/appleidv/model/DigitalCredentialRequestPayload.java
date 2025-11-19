package com.example.appleidv.model;

import java.util.List;

public record DigitalCredentialRequestPayload(
        String protocol,
        String docType,
        String mediator,
        List<RequestedNamespace> namespaces,
        String challenge,
        String relyingPartyId,
        String sessionToken
) {

    public record RequestedNamespace(
            String namespace,
            List<String> dataElements
    ) {
    }
}

