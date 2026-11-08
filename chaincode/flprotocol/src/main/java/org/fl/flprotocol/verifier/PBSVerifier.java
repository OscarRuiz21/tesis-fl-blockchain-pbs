package org.fl.flprotocol.verifier;

import org.bouncycastle.math.ec.ECPoint;
import org.fl.flprotocol.crypto.CryptoCodec;
import org.fl.flprotocol.crypto.HashToCurve;
import org.fl.flprotocol.crypto.PBSDomainParams;
import org.fl.flprotocol.crypto.ProtocolHash;

import java.math.BigInteger;

/**
 * Verificación de la credencial PBS (Abe-Okamoto sobre NIST P-256, §4.2.2):
 * <pre>
 *   ω + δ  ≡  H( (ρG + ωX_AS) ‖ (σG + δZ_r) ‖ Z_r ‖ anon_cid )  (mod n)
 * </pre>
 * donde {@code σ_PBS = (ρ, ω, σ, δ)} es la credencial descegada,
 * {@code Z_r = H_curve(r)} se recalcula on-chain a partir de la ronda y
 * {@code X_AS} es la clave pública del AS.
 *
 * <p>Cuesta cuatro multiplicaciones escalares, un Hash-to-Curve y un hash
 * final. Cualquier excepción al decodificar se reduce a {@code false}, y el
 * contrato rechaza con {@code INVALID_PBS_SIGNATURE} sin filtrar diagnóstico.</p>
 */
public final class PBSVerifier {

    private final PBSDomainParams params;

    public PBSVerifier(PBSDomainParams params) {
        this.params = params;
    }

    /**
     * @param rhoB64     {@code ρ = r_ans + t_1 mod n} en Base64.
     * @param omegaB64   {@code ω = c + t_2 mod n} en Base64.
     * @param sigmaB64   {@code σ = s + t_3 mod n} en Base64.
     * @param deltaB64   {@code δ = d + t_4 mod n} en Base64.
     * @param anonCidB64 {@code anon_cid = anon_sk·G}, punto sin comprimir (65 bytes).
     * @param roundId    ronda activa, entrada de {@code H_curve} para {@code Z_r}.
     * @param xasB64     clave pública del AS, {@code X_AS = x_AS·G}.
     */
    public boolean verify(
            String rhoB64,
            String omegaB64,
            String sigmaB64,
            String deltaB64,
            String anonCidB64,
            long roundId,
            String xasB64) {
        try {
            BigInteger rho = CryptoCodec.decodeScalar(rhoB64, params.n());
            BigInteger omega = CryptoCodec.decodeScalar(omegaB64, params.n());
            BigInteger sigma = CryptoCodec.decodeScalar(sigmaB64, params.n());
            BigInteger delta = CryptoCodec.decodeScalar(deltaB64, params.n());

            ECPoint anonCid = CryptoCodec.decodePoint(anonCidB64, params.curve());
            ECPoint XAS = CryptoCodec.decodePoint(xasB64, params.curve());

            ECPoint Zr = HashToCurve.compute(roundId, params);

            // Reconstrucción de los compromisos cegados:
            //   α' = ρG + ωX_AS,  β' = σG + δZ_r
            ECPoint alphaPrime = params.G().multiply(rho)
                .add(XAS.multiply(omega)).normalize();
            ECPoint betaPrime = params.G().multiply(sigma)
                .add(Zr.multiply(delta)).normalize();

            BigInteger rhs = ProtocolHash.computeAbeOkamotoChallenge(
                alphaPrime, betaPrime, Zr, anonCid).mod(params.n());
            BigInteger lhs = omega.add(delta).mod(params.n());

            return lhs.equals(rhs);
        } catch (Exception e) {
            return false;
        }
    }
}
