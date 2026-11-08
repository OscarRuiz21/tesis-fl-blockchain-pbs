package org.fl.as.store;

import org.fl.as.model.ClientId;
import org.fl.as.model.IssuanceRecord;
import org.fl.as.model.RoundId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IssuanceLedgerTest {

    private IssuanceLedger ledger;

    @BeforeEach
    void setUp() {
        ledger = new IssuanceLedger();
    }

    @Test
    void tryRecordReturnsTrueOnFirstCall() {
        boolean result = ledger.tryRecord(new IssuanceRecord(
            ClientId.of("client-1"), RoundId.of(1)));

        assertThat(result).isTrue();
    }

    @Test
    void tryRecordReturnsFalseOnDuplicate() {
        IssuanceRecord r = new IssuanceRecord(ClientId.of("client-1"), RoundId.of(1));

        ledger.tryRecord(r);
        boolean second = ledger.tryRecord(r);

        assertThat(second).isFalse();
    }

    @Test
    void containsChecksPresence() {
        ClientId client = ClientId.of("client-1");
        RoundId round = RoundId.of(2);

        assertThat(ledger.contains(client, round)).isFalse();

        ledger.tryRecord(new IssuanceRecord(client, round));
        assertThat(ledger.contains(client, round)).isTrue();
        assertThat(ledger.contains(client, RoundId.of(3))).isFalse();
    }
}
