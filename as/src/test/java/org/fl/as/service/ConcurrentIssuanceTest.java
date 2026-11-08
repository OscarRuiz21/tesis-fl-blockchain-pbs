package org.fl.as.service;

import org.fl.as.crypto.PBSDomainParams;
import org.fl.as.exception.DuplicateCredentialRequestException;
import org.fl.as.model.ClientId;
import org.fl.as.model.CommitmentIssued;
import org.fl.as.model.RoundId;
import org.fl.as.store.EligibilityRegistry;
import org.fl.as.store.IssuanceLedger;
import org.fl.as.store.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba dos afirmaciones del Cap. 5 sobre la emisión: N peticiones
 * concurrentes de la misma (identidad, ronda) producen exactamente una
 * credencial, porque la comprobación y el registro son una sola operación
 * atómica; y las peticiones de identidades distintas no se serializan, sino
 * que se solapan en el tiempo.
 */
class ConcurrentIssuanceTest {

    private static final RoundId ROUND = RoundId.of(1);
    private static final int THREADS = 50;

    private EligibilityRegistry eligibility;
    private IssueCommitmentService service;

    @BeforeEach
    void setUp() {
        PBSDomainParams params = PBSDomainParams.p256();
        eligibility = new EligibilityRegistry();
        Clock clock = Clock.fixed(Instant.parse("2026-09-06T18:00:00Z"), ZoneOffset.UTC);
        service = new IssueCommitmentService(
            params, new SecureRandom(), eligibility, new IssuanceLedger(), new SessionStore(), clock);
    }

    @Test
    void sameIdentitySameRoundConcurrentlyYieldsExactlyOneCredential() throws Exception {
        ClientId client = ClientId.of("client-1");
        eligibility.registerAll(List.of(client), 5);
        AtomicInteger issued = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    service.issue(client, ROUND);
                    issued.incrementAndGet();
                } catch (DuplicateCredentialRequestException e) {
                    duplicates.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get();
        pool.shutdown();
        System.out.printf("Atomicidad: %d peticiones concurrentes misma identidad+ronda -> emitidas=%d, duplicadas=%d%n",
            THREADS, issued.get(), duplicates.get());
        assertThat(issued.get()).isEqualTo(1);
        assertThat(duplicates.get()).isEqualTo(THREADS - 1);
    }

    @Test
    void distinctIdentitiesAreServedOverlapped() throws Exception {
        int clients = 10;
        List<ClientId> ids = new ArrayList<>();
        List<ClientId> seqIds = new ArrayList<>();
        List<ClientId> warmIds = new ArrayList<>();
        for (int i = 1; i <= clients; i++) { ids.add(ClientId.of("client-" + i)); seqIds.add(ClientId.of("seq-" + i)); }
        for (int w = 0; w < 20; w++) warmIds.add(ClientId.of("warm-" + w));
        List<ClientId> all = new ArrayList<>(); all.addAll(ids); all.addAll(seqIds); all.addAll(warmIds);
        eligibility.registerAll(all, 5);   // registerAll sustituye el conjunto: una sola llamada
        // Calentamiento de la JVM.
        for (ClientId warm : warmIds) service.issue(warm, ROUND);
        long seqStart = System.nanoTime();
        for (ClientId id : seqIds) service.issue(id, ROUND);
        long seqTotal = System.nanoTime() - seqStart;
        // Todos los hilos arrancan a la vez.
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        List<Future<Long>> futures = new ArrayList<>();
        for (ClientId id : ids) {
            futures.add(pool.submit((Callable<Long>) () -> {
                start.await();
                long t0 = System.nanoTime();
                CommitmentIssued c = service.issue(id, ROUND);
                assertThat(c.sessionId()).isNotNull();
                return System.nanoTime() - t0;
            }));
        }
        long parStart = System.nanoTime();
        start.countDown();
        long sumIndividual = 0;
        for (Future<Long> f : futures) sumIndividual += f.get();
        long parTotal = System.nanoTime() - parStart;
        pool.shutdown();
        System.out.printf("Paralelismo: %d clientes: secuencial=%.2f ms, concurrente total=%.2f ms, suma de duraciones individuales=%.2f ms, nucleos=%d%n",
            clients, seqTotal / 1e6, parTotal / 1e6, sumIndividual / 1e6, Runtime.getRuntime().availableProcessors());
        // Si el servicio serializara, el total concurrente no podría ser menor que la suma de duraciones individuales.
        assertThat(parTotal).isLessThan(sumIndividual);
    }
}
