package org.fl.client.model;

import org.fl.client.crypto.CryptoCodec;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Credencial PBS descegada {@code σ_PBS = (ρ, ω, σ, δ)} que el cliente
 * presenta al chaincode en la Etapa 2. El chaincode verifica
 * <pre>
 *   ω + δ ≡ H((ρG + ωX_AS) ‖ (σG + δZ_r) ‖ Z_r ‖ anon_cid) (mod n)
 * </pre>
 * con {@code ρ, ω} de la Rama A y {@code σ, δ} de la Rama B (ver
 * {@code PBSClientOperations.unblind}).
 */
public record Credential(
        BigInteger rho,
        BigInteger omega,
        BigInteger sigma,
        BigInteger delta
) {
    public Credential {
        Objects.requireNonNull(rho, "rho must not be null");
        Objects.requireNonNull(omega, "omega must not be null");
        Objects.requireNonNull(sigma, "sigma must not be null");
        Objects.requireNonNull(delta, "delta must not be null");
        if (rho.signum() <= 0) {
            throw new IllegalArgumentException("Credential rho must be positive");
        }
        if (omega.signum() <= 0) {
            throw new IllegalArgumentException("Credential omega must be positive");
        }
        if (sigma.signum() <= 0) {
            throw new IllegalArgumentException("Credential sigma must be positive");
        }
        if (delta.signum() <= 0) {
            throw new IllegalArgumentException("Credential delta must be positive");
        }
    }

    public String rhoB64()   { return CryptoCodec.encodeScalar(rho); }
    public String omegaB64() { return CryptoCodec.encodeScalar(omega); }
    public String sigmaB64() { return CryptoCodec.encodeScalar(sigma); }
    public String deltaB64() { return CryptoCodec.encodeScalar(delta); }
}
