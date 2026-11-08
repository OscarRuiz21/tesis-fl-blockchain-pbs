package org.fl.client.gateway;

import org.fl.client.exception.BlockchainUnavailableException;
import org.fl.client.exception.ChaincodeProtocolException;
import org.fl.client.exception.DuplicateNullifierException;
import org.fl.client.exception.InvalidPBSSignatureException;
import org.fl.client.exception.RoundNotActiveException;
import org.fl.client.model.AnonId;
import org.fl.client.model.RoundId;
import org.springframework.stereotype.Component;

/**
 * Traduce los códigos de error del chaincode y los conflictos de Fabric.
 * {@code INVALID_SIG_MODEL} (P6) se reporta igual que una credencial
 * inválida. Si por relevo de líder dos clientes agregan a la vez, el
 * conflicto MVCC resultante se trata como aviso, no como falla.
 */
@Component
public class FabricErrorTranslator {

    /** {@code anonCid} y {@code roundId} pueden ser null; sin ellos se degrada a {@link ChaincodeProtocolException}. */
    public RuntimeException translateChaincodeMessage(
            String chaincodeMessage, AnonId anonCid, RoundId roundId) {
        if (chaincodeMessage == null) {
            return new ChaincodeProtocolException("<no message>");
        }
        if (chaincodeMessage.startsWith("ROUND_NOT_ACTIVE:")) {
            String[] parts = chaincodeMessage.split(":");
            if (parts.length >= 3) {
                long requested = Long.parseLong(parts[1]);
                long active = Long.parseLong(parts[2]);
                return new RoundNotActiveException(RoundId.of(requested), RoundId.of(active));
            }
            return new ChaincodeProtocolException(chaincodeMessage);
        }
        if (chaincodeMessage.startsWith("DUPLICATE_NULLIFIER:")) {
            if (anonCid != null && roundId != null) {
                return new DuplicateNullifierException(roundId, anonCid);
            }
            return new ChaincodeProtocolException(chaincodeMessage);
        }
        if (chaincodeMessage.startsWith("INVALID_PBS_SIGNATURE")) {
            if (anonCid != null && roundId != null) {
                return new InvalidPBSSignatureException(anonCid, roundId);
            }
            return new ChaincodeProtocolException(chaincodeMessage);
        }
        if (chaincodeMessage.startsWith("INVALID_SIG_MODEL")) {
            if (anonCid != null && roundId != null) {
                return new InvalidPBSSignatureException(anonCid, roundId);
            }
            return new ChaincodeProtocolException(chaincodeMessage);
        }
        return new ChaincodeProtocolException(chaincodeMessage);
    }

    /** Otra transacción escribió las mismas claves mientras esta estaba en vuelo. */
    public boolean isMvccReadConflict(Throwable t) {
        if (t == null) return false;
        String msg = t.getMessage();
        if (msg != null && msg.contains("MVCC_READ_CONFLICT")) return true;
        return t.getCause() != null && isMvccReadConflict(t.getCause());
    }

    /**
     * Otra transacción escribió en un rango leído por esta (p. ej. un
     * {@code getStateByRange}). Es transitorio y se resuelve reintentando.
     */
    public boolean isPhantomReadConflict(Throwable t) {
        if (t == null) return false;
        String msg = t.getMessage();
        if (msg != null && msg.contains("PHANTOM_READ_CONFLICT")) return true;
        return t.getCause() != null && isPhantomReadConflict(t.getCause());
    }

    public boolean isRetryableConflict(Throwable t) {
        return isMvccReadConflict(t) || isPhantomReadConflict(t);
    }

    public BlockchainUnavailableException unavailable(Throwable cause) {
        return new BlockchainUnavailableException(cause);
    }
}
