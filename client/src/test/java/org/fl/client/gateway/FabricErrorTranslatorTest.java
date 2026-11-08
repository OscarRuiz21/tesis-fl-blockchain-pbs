package org.fl.client.gateway;

import org.fl.client.exception.ChaincodeProtocolException;
import org.fl.client.exception.DuplicateNullifierException;
import org.fl.client.exception.InvalidPBSSignatureException;
import org.fl.client.exception.RoundNotActiveException;
import org.fl.client.crypto.PBSDomainParams;
import org.fl.client.model.AnonId;
import org.fl.client.model.RoundId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FabricErrorTranslatorTest {

    private FabricErrorTranslator translator;
    private AnonId sampleAnonCid;
    private RoundId sampleRound;

    @BeforeEach
    void setUp() {
        translator = new FabricErrorTranslator();
        var p = PBSDomainParams.p256();
        sampleAnonCid = AnonId.of(p.G().multiply(BigInteger.valueOf(7)).normalize());
        sampleRound = RoundId.of(1);
    }

    @Test
    void mapsRoundNotActive() {
        var ex = translator.translateChaincodeMessage("ROUND_NOT_ACTIVE:1:5", sampleAnonCid, sampleRound);

        assertThat(ex).isInstanceOf(RoundNotActiveException.class);
        RoundNotActiveException rne = (RoundNotActiveException) ex;
        assertThat(rne.getRequested().value()).isEqualTo(1L);
        assertThat(rne.getActive().value()).isEqualTo(5L);
    }

    @Test
    void mapsDuplicateNullifier() {
        var ex = translator.translateChaincodeMessage("DUPLICATE_NULLIFIER:abc123", sampleAnonCid, sampleRound);

        assertThat(ex).isInstanceOf(DuplicateNullifierException.class);
    }

    @Test
    void mapsInvalidPbsSignature() {
        var ex = translator.translateChaincodeMessage("INVALID_PBS_SIGNATURE", sampleAnonCid, sampleRound);

        assertThat(ex).isInstanceOf(InvalidPBSSignatureException.class);
    }

    @Test
    void mapsInvalidSigModelAsInvalidPbsSignature() {
        var ex = translator.translateChaincodeMessage("INVALID_SIG_MODEL", sampleAnonCid, sampleRound);

        assertThat(ex).isInstanceOf(InvalidPBSSignatureException.class);
    }

    @Test
    void unknownMessageBecomesChaincodeProtocol() {
        var ex = translator.translateChaincodeMessage("SOMETHING_NEW:foo", null, null);
        assertThat(ex).isInstanceOf(ChaincodeProtocolException.class);
    }

    @Test
    void detectsMvccReadConflict() {
        Throwable t = new RuntimeException("MVCC_READ_CONFLICT detected");
        assertThat(translator.isMvccReadConflict(t)).isTrue();
        assertThat(translator.isMvccReadConflict(new RuntimeException("other"))).isFalse();
        assertThat(translator.isMvccReadConflict(null)).isFalse();
    }
}
