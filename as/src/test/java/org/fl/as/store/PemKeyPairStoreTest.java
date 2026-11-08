package org.fl.as.store;

import org.fl.as.crypto.PBSDomainParams;
import org.fl.as.model.ASKeyPair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PemKeyPairStoreTest {

    private final PBSDomainParams params = PBSDomainParams.p256();
    private final SecureRandom random = new SecureRandom();

    @Test
    void generatesAndWritesWhenPathDoesNotExist(@TempDir Path tmp) throws IOException {
        Path target = tmp.resolve("as-keypair.pem");
        assertThat(Files.exists(target)).isFalse();

        ASKeyPair pair = PemKeyPairStore.loadOrGenerate(target, params, random);

        assertThat(Files.exists(target)).isTrue();
        assertThat(pair).isNotNull();
        assertThat(pair.privateKey().signum()).isPositive();
        assertThat(pair.privateKey().compareTo(params.n())).isLessThan(0);
        assertThat(pair.publicKey().isInfinity()).isFalse();
        // X_AS = x_AS·G
        assertThat(pair.publicKey().normalize())
            .isEqualTo(params.G().multiply(pair.privateKey()).normalize());
    }

    @Test
    void loadsExistingPemWhenPathExists(@TempDir Path tmp) throws IOException {
        Path target = tmp.resolve("as-keypair.pem");
        ASKeyPair generated = PemKeyPairStore.loadOrGenerate(target, params, random);

        // Con el archivo presente no se regenera la llave.
        ASKeyPair loaded = PemKeyPairStore.loadOrGenerate(target, params, random);

        assertThat(loaded.privateKey()).isEqualTo(generated.privateKey());
        assertThat(loaded.publicKey().normalize()).isEqualTo(generated.publicKey().normalize());
    }

    @Test
    void roundtripRecoversIdenticalKeypair(@TempDir Path tmp) throws IOException {
        Path target = tmp.resolve("as-keypair.pem");
        ASKeyPair original = ASKeyPair.generate(params, random);

        PemKeyPairStore.write(target, original);
        ASKeyPair loaded = PemKeyPairStore.load(target, params);

        assertThat(loaded.privateKey()).isEqualTo(original.privateKey());
        assertThat(loaded.publicKey().normalize()).isEqualTo(original.publicKey().normalize());
    }

    @Test
    void filePermissionsAre0600OnPosix(@TempDir Path tmp) throws IOException {
        Path target = tmp.resolve("as-keypair.pem");
        // Se omite fuera de POSIX; el AS corre en un contenedor Linux.
        boolean posix = Files.getFileAttributeView(tmp, PosixFileAttributeView.class) != null;
        if (!posix) return;

        PemKeyPairStore.loadOrGenerate(target, params, random);

        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(target);
        assertThat(perms).containsExactlyInAnyOrder(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);
    }
}
