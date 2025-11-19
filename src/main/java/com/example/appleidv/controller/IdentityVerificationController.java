package com.example.appleidv.controller;

import com.example.appleidv.model.IdentityResultRequest;
import com.example.appleidv.model.IdentitySessionResponse;
import com.example.appleidv.model.IdentitySession;
import com.example.appleidv.service.IdentitySessionService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@RestController
@RequestMapping("/api/idv")
public class IdentityVerificationController {

    private final IdentitySessionService sessionService;

    public IdentityVerificationController(IdentitySessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/session")
    public IdentitySessionResponse createSession(HttpServletRequest request) {
        return sessionService.startSession(resolveBaseUrl(request));
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<IdentitySessionResponse> resumeSession(@PathVariable String sessionId) {
        try {
            return ResponseEntity.ok(sessionService.resumeSession(sessionId));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @GetMapping("/session/{sessionId}/status")
    public ResponseEntity<IdentitySession.IdentitySessionSnapshot> getStatus(@PathVariable String sessionId) {
        return sessionService.findSession(sessionId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/session/{sessionId}/result")
    public ResponseEntity<Void> reportResult(@PathVariable String sessionId,
                                             @Valid @RequestBody IdentityResultRequest resultRequest) {
        try {
            sessionService.reportResult(sessionId, resultRequest);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/session/{sessionId}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQrCode(@PathVariable String sessionId) throws IOException {
        IdentitySession.IdentitySessionSnapshot snapshot = sessionService.findSession(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        byte[] png = renderQr(snapshot.qrContent());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"session-" + sessionId + ".png\"")
                .body(png);
    }

    private String resolveBaseUrl(HttpServletRequest request) {
        String base = UriComponentsBuilder.newInstance()
                .scheme(request.getScheme())
                .host(request.getServerName())
                .port(request.getServerPort())
                .path(request.getContextPath())
                .build()
                .toUriString();
        return base.endsWith("/") ? base : base + "/";
    }

    private byte[] renderQr(String content) throws IOException {
        QRCodeWriter writer = new QRCodeWriter();
        try {
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 360, 360);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", baos);
            return baos.toByteArray();
        } catch (WriterException e) {
            throw new IOException("Failed to render QR code", e);
        }
    }
}

