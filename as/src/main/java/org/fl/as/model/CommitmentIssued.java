package org.fl.as.model;

/** Respuesta del commit: sesión y compromisos {@code A}, {@code B} en Base64. */
public record CommitmentIssued(SessionId sessionId, String aB64, String bB64) {}
