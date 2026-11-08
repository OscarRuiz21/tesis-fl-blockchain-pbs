package org.fl.client.store;

import org.fl.client.model.MasterSecret;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MasterSecretStoreTest {

    private final SecureRandom random = new SecureRandom();

    @Test
    void generatesAndWritesWhenPathDoesNotExist(@TempDir Path tmp) throws IOException {
        Path target = tmp.resolve("master-secret.bin");
        assertThat(Files.exists(target)).isFalse();

        MasterSecret secret = MasterSecretStore.loadOrGenerate(target, random);

        assertThat(Files.exists(target)).isTrue();
        assertThat(Files.size(target)).isEqualTo(MasterSecret.LENGTH);
        assertThat(secret.bytes()).hasSize(MasterSecret.LENGTH);
    }

    @Test
    void loadsExistingFileWithoutRegenerating(@TempDir Path tmp) throws IOException {
        Path target = tmp.resolve("master-secret.bin");
        MasterSecret generated = MasterSecretStore.loadOrGenerate(target, random);

        MasterSecret loaded = MasterSecretStore.loadOrGenerate(target, random);

        assertThat(loaded).isEqualTo(generated);
        assertThat(loaded.bytes()).containsExactly(generated.bytes());
    }

    @Test
    void roundtripRecoversIdenticalBytes(@TempDir Path tmp) throws IOException {
        Path target = tmp.resolve("master-secret.bin");
        MasterSecret original = MasterSecret.generate(random);

        MasterSecretStore.write(target, original);
        MasterSecret loaded = MasterSecretStore.load(target);

        assertThat(loaded).isEqualTo(original);
    }

    @Test
    void filePermissionsAre0600OnPosix(@TempDir Path tmp) throws IOException {
        Path target = tmp.resolve("master-secret.bin");
        boolean posix = Files.getFileAttributeView(tmp, PosixFileAttributeView.class) != null;
        if (!posix) return;

        MasterSecretStore.loadOrGenerate(target, random);

        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(target);
        assertThat(perms).containsExactlyInAnyOrder(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);
    }

    @Test
    void failsLoudWhenFileSizeIsIncorrect(@TempDir Path tmp) throws IOException {
        Path target = tmp.resolve("corrupted.bin");
        Files.write(target, new byte[]{1, 2, 3});  // tamaño incorrecto

        assertThatThrownBy(() -> MasterSecretStore.loadOrGenerate(target, random))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("3 bytes")
            .hasMessageContaining("expected " + MasterSecret.LENGTH);
    }

    @Test
    void createsParentDirectoryIfMissing(@TempDir Path tmp) throws IOException {
        Path target = tmp.resolve("nested/dirs/master-secret.bin");

        MasterSecret secret = MasterSecretStore.loadOrGenerate(target, random);

        assertThat(Files.exists(target)).isTrue();
        assertThat(secret).isNotNull();
    }
}
