package art.arcane.react.web;

import art.arcane.react.api.web.relay.ReactIdentityStore;
import art.arcane.react.api.web.relay.ReactServerIdentity;
import art.arcane.react.api.web.relay.RelayHandshake;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RelayHandshakeTest {

    private static final Base64.Decoder B64URL_DECODER = Base64.getUrlDecoder();

    @Test
    void signNonceVerifiesAgainstNonceBytes(@TempDir File tempDir) throws IOException {
        ReactServerIdentity identity = ReactIdentityStore.loadOrCreate(tempDir);
        byte[] nonce = RelayHandshake.newNonce();

        String sig = RelayHandshake.signNonce(identity, nonce);
        assertNotNull(sig);

        boolean result = ReactServerIdentity.verify(
                identity.publicKeyBytes(),
                B64URL_DECODER.decode(sig),
                nonce
        );
        assertTrue(result, "signNonce must produce a signature that verifies over the raw nonce bytes");
    }

    @Test
    void signNonceEmitsUrlSafeBase64WithoutPadding(@TempDir File tempDir) throws IOException {
        ReactServerIdentity identity = ReactIdentityStore.loadOrCreate(tempDir);
        byte[] nonce = RelayHandshake.newNonce();

        String sig = RelayHandshake.signNonce(identity, nonce);
        assertFalse(sig.contains("="), "signature must not contain padding");
        assertFalse(sig.contains("+"), "signature must not contain standard base64 chars");
        assertFalse(sig.contains("/"), "signature must not contain standard base64 chars");
    }

    @Test
    void tamperedNonceFails(@TempDir File tempDir) throws IOException {
        ReactServerIdentity identity = ReactIdentityStore.loadOrCreate(tempDir);
        byte[] nonce = RelayHandshake.newNonce();

        String sig = RelayHandshake.signNonce(identity, nonce);

        byte[] differentNonce = RelayHandshake.newNonce();
        boolean result = ReactServerIdentity.verify(
                identity.publicKeyBytes(),
                B64URL_DECODER.decode(sig),
                differentNonce
        );
        assertFalse(result, "verify must return false when the nonce is tampered");
    }

    @Test
    void wrongKeyFails(@TempDir File tempDir1, @TempDir File tempDir2) throws IOException {
        ReactServerIdentity identity1 = ReactIdentityStore.loadOrCreate(tempDir1);
        ReactServerIdentity identity2 = ReactIdentityStore.loadOrCreate(tempDir2);

        byte[] nonce = RelayHandshake.newNonce();
        String sig = RelayHandshake.signNonce(identity1, nonce);

        boolean result = ReactServerIdentity.verify(
                identity2.publicKeyBytes(),
                B64URL_DECODER.decode(sig),
                nonce
        );
        assertFalse(result, "verify must return false when a different key is used");
    }

    @Test
    void newNonceIsThirtyTwoBytesAndDistinct() {
        byte[] first = RelayHandshake.newNonce();
        byte[] second = RelayHandshake.newNonce();

        assertEquals(32, first.length, "nonce must be 32 bytes");
        assertEquals(32, second.length, "nonce must be 32 bytes");
        assertFalse(java.util.Arrays.equals(first, second), "successive nonces must differ");
    }
}
