package org.fl.as.model;

/**
 * Tupla {@code (r_ans, c, s, d)} que el cliente descega para obtener la
 * credencial {@code σ_PBS = (ρ, ω, σ, δ)}.
 */
public record SignatureIssued(String rAnsB64, String cB64, String sB64, String dB64) {}
