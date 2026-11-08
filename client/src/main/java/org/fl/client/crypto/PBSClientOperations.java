package org.fl.client.crypto;

import org.bouncycastle.math.ec.ECPoint;
import org.fl.client.model.AnonId;
import org.fl.client.model.AnonSecretKey;
import org.fl.client.model.BlindResult;
import org.fl.client.model.Credential;
import org.fl.client.model.MasterSecret;
import org.fl.client.model.RoundId;
import org.fl.client.model.SignatureResponse;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * Operaciones de la PBS del lado del cliente (Cap. 4, §4.2.1): derivación de
 * {@code anon_cid}, cegado, descegado y verificación local de la credencial.
 */
public final class PBSClientOperations {

    private PBSClientOperations() {}

    /**
     * Deriva la identidad anónima de la ronda determinísticamente:
     * {@code anon_sk = H(master_sk ∥ r) mod n}, {@code anon_cid = anon_sk · G}.
     *
     * <p>{@code anon_cid} es un punto, no un hash de 32 bytes. Sirve a la vez
     * de llave pública para verificar {@code σ_model} (P6), de entrada al reto
     * Fiat-Shamir y de nulificador.</p>
     */
    public static AnonId deriveAnonId(MasterSecret master, RoundId roundId,
                                       PBSDomainParams params) {
        AnonSecretKey anonSk = AnonSecretKey.derive(master, roundId, params);
        ECPoint anonCidPoint = params.G().multiply(anonSk.value()).normalize();
        return AnonId.of(anonCidPoint);
    }

    /**
     * Cegado del cliente:
     * <pre>
     *   Z_r = H_curve(r)
     *   α   = A + t_1·G + t_2·X_AS
     *   β   = B + t_3·G + t_4·Z_r
     *   ε   = H(α ‖ β ‖ Z_r ‖ anon_cid)
     *   e   = ε − t_2 − t_4 mod n
     * </pre>
     *
     * <p>{@code α} y {@code β} no salen del cliente: el AS solo recibe el
     * escalar {@code e}. Se guardan {@code t_1..t_4} para descegar.</p>
     */
    public static BlindResult blind(
            ECPoint A,
            ECPoint B,
            AnonId anonCid,
            RoundId roundId,
            ECPoint xAS,
            PBSDomainParams params,
            SecureRandom random) {

        ECPoint Zr = HashToCurve.compute(roundId.value(), params);

        BigInteger t1 = CryptoCodec.randomScalar(params.n(), random);
        BigInteger t2 = CryptoCodec.randomScalar(params.n(), random);
        BigInteger t3 = CryptoCodec.randomScalar(params.n(), random);
        BigInteger t4 = CryptoCodec.randomScalar(params.n(), random);

        ECPoint alpha = A
            .add(params.G().multiply(t1))
            .add(xAS.multiply(t2))
            .normalize();
        ECPoint beta = B
            .add(params.G().multiply(t3))
            .add(Zr.multiply(t4))
            .normalize();

        BigInteger eps = ProtocolHash.computeAbeOkamotoChallenge(
            alpha, beta, Zr, anonCid.point());
        BigInteger e = eps.subtract(t2).subtract(t4).mod(params.n());

        // BlindResult exige e > 0. Si e == 0 (probabilidad despreciable) se usa
        // 1; la credencial resultante no verifica y verifyLocally la rechaza.
        if (e.signum() <= 0) {
            e = BigInteger.ONE;
        }

        return new BlindResult(e, t1, t2, t3, t4);
    }

    /**
     * Descegado del cliente:
     * <pre>
     *   ρ = r_ans + t_1 mod n
     *   ω = c     + t_2 mod n
     *   σ = s     + t_3 mod n
     *   δ = d     + t_4 mod n
     * </pre>
     */
    public static Credential unblind(
            SignatureResponse sigResp,
            BlindResult blind,
            BigInteger n) {
        BigInteger rho   = sigResp.rAns().add(blind.t1()).mod(n);
        BigInteger omega = sigResp.c().add(blind.t2()).mod(n);
        BigInteger sigma = sigResp.s().add(blind.t3()).mod(n);
        BigInteger delta = sigResp.d().add(blind.t4()).mod(n);

        // Mismo tratamiento del cero que en blind.
        if (rho.signum() <= 0) rho = BigInteger.ONE;
        if (omega.signum() <= 0) omega = BigInteger.ONE;
        if (sigma.signum() <= 0) sigma = BigInteger.ONE;
        if (delta.signum() <= 0) delta = BigInteger.ONE;

        return new Credential(rho, omega, sigma, delta);
    }

    /**
     * Verificación local previa al submit:
     * {@code ω + δ ≡ H((ρG + ωX_AS) ‖ (σG + δZ_r) ‖ Z_r ‖ anon_cid) (mod n)}.
     *
     * <p>Es la misma ecuación del chaincode; se evalúa antes para no gastar
     * una transacción con una credencial inválida.</p>
     */
    public static boolean verifyLocally(
            Credential cred,
            AnonId anonCid,
            RoundId roundId,
            ECPoint xAS,
            PBSDomainParams params) {
        try {
            ECPoint Zr = HashToCurve.compute(roundId.value(), params);
            ECPoint alphaPrime = params.G().multiply(cred.rho())
                .add(xAS.multiply(cred.omega())).normalize();
            ECPoint betaPrime = params.G().multiply(cred.sigma())
                .add(Zr.multiply(cred.delta())).normalize();

            BigInteger rhs = ProtocolHash.computeAbeOkamotoChallenge(
                alphaPrime, betaPrime, Zr, anonCid.point()).mod(params.n());
            BigInteger lhs = cred.omega().add(cred.delta()).mod(params.n());
            return lhs.equals(rhs);
        } catch (Exception e) {
            return false;
        }
    }
}
