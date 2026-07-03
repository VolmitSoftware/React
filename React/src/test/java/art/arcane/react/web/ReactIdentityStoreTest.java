package art.arcane.react.web;

import art.arcane.react.api.web.relay.ReactIdentityStore;
import art.arcane.react.api.web.relay.ReactServerIdentity;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReactIdentityStoreTest {

    @Test
    void loadOrCreateWritesKeyFiles(@TempDir File tempDir) throws IOException {
        ReactServerIdentity identity = ReactIdentityStore.loadOrCreate(tempDir);

        assertNotNull(identity);
        assertTrue(new File(tempDir, "web/server.key").exists(), "web/server.key must exist");
        assertTrue(new File(tempDir, "web/server.pub").exists(), "web/server.pub must exist");
        assertTrue(new File(tempDir, "web/server.key").length() > 0, "web/server.key must not be empty");
        assertTrue(new File(tempDir, "web/server.pub").length() > 0, "web/server.pub must not be empty");
    }

    @Test
    void reloadYieldsIdenticalPublicKey(@TempDir File tempDir) throws IOException {
        ReactServerIdentity first = ReactIdentityStore.loadOrCreate(tempDir);
        ReactServerIdentity second = ReactIdentityStore.loadOrCreate(tempDir);

        assertEquals(first.publicKeyBase64(), second.publicKeyBase64(),
                "Reloading must yield the same public key, not regenerate");
    }

    @Test
    void fingerprintIs64LowercaseHexAndStable(@TempDir File tempDir) throws IOException {
        ReactServerIdentity first = ReactIdentityStore.loadOrCreate(tempDir);
        ReactServerIdentity second = ReactIdentityStore.loadOrCreate(tempDir);

        String fp = first.fingerprint();
        assertNotNull(fp);
        assertEquals(64, fp.length(), "Fingerprint must be 64 hex chars");
        assertTrue(fp.matches("[0-9a-f]{64}"), "Fingerprint must be lowercase hex: " + fp);
        assertEquals(fp, second.fingerprint(), "Fingerprint must be stable across reload");
    }

    @Test
    void privateKeyFilePermissionsAreOwnerOnly(@TempDir File tempDir) throws IOException {
        Assumptions.assumeTrue(
                FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "POSIX permissions not supported on this filesystem"
        );

        ReactIdentityStore.loadOrCreate(tempDir);

        File keyFile = new File(tempDir, "web/server.key");
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(keyFile.toPath());
        Set<PosixFilePermission> expected = Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE
        );
        assertEquals(expected, perms, "server.key must be OWNER_READ|OWNER_WRITE only");
    }

    @Test
    void signAndVerifyRoundtrip(@TempDir File tempDir) throws IOException {
        ReactServerIdentity identity = ReactIdentityStore.loadOrCreate(tempDir);

        byte[] payload = "hello react relay".getBytes();
        byte[] sig = identity.sign(payload);

        assertTrue(ReactServerIdentity.verify(identity.publicKeyBytes(), sig, payload),
                "verify must return true for a valid signature");

        byte[] tampered = payload.clone();
        tampered[0] ^= 0x01;
        assertFalse(ReactServerIdentity.verify(identity.publicKeyBytes(), sig, tampered),
                "verify must return false when payload is flipped");
    }
}
