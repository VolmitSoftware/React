package art.arcane.react.web;

import art.arcane.react.api.web.WebConfiguration;
import art.arcane.react.api.web.dto.IdentityDto;
import art.arcane.react.api.web.relay.ReactServerIdentity;
import art.arcane.react.core.controller.WebController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebControllerIdentityTest {

    private WebController buildController(File dataFolder) {
        WebController c = new WebController() {
            @Override
            protected void executeAsync(Runnable r) {
                Thread.ofVirtual().start(r);
            }

            @Override
            protected IdentityDto resolveIdentity() {
                IdentityDto dto = new IdentityDto();
                dto.version = "0.0.0-test";
                dto.serverName = "TestServer";
                dto.folia = false;
                dto.serverId = "127.0.0.1:25565";
                return dto;
            }
        };
        WebConfiguration cfg = new WebConfiguration();
        cfg.setListenerEnabled(false);
        c.setConfig(cfg);
        c.setDataFolder(dataFolder);
        return c;
    }

    @Test
    void loadAuthCreatesIdentity(@TempDir File dataFolder) throws Exception {
        WebController c = buildController(dataFolder);
        c.loadAuth();

        ReactServerIdentity identity = c.getIdentity();
        assertNotNull(identity, "getIdentity() must not be null after loadAuth()");

        File keyFile = new File(dataFolder, "web/server.key");
        assertTrue(keyFile.exists(), "web/server.key must exist after loadAuth()");

        String fp = identity.fingerprint();
        assertNotNull(fp, "fingerprint() must not be null");
        assertEquals(64, fp.length(), "fingerprint must be 64 hex characters (SHA-256)");
        assertTrue(fp.matches("[0-9a-f]{64}"), "fingerprint must be lowercase hex");
    }

    @Test
    void identityStableAcrossReload(@TempDir File dataFolder) throws Exception {
        WebController first = buildController(dataFolder);
        first.loadAuth();
        String pubKey1 = first.getIdentity().publicKeyBase64();

        WebController second = buildController(dataFolder);
        second.loadAuth();
        String pubKey2 = second.getIdentity().publicKeyBase64();

        assertEquals(pubKey1, pubKey2, "publicKeyBase64() must be identical across reloads from same folder");
    }
}
