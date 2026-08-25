package art.arcane.react.web;

import art.arcane.react.api.web.PairingToken;
import art.arcane.react.api.web.PlayerBackend;
import art.arcane.react.api.web.TokenRecord;
import art.arcane.react.api.web.TokenStore;
import art.arcane.react.api.web.WebMutation;
import art.arcane.react.api.web.dto.Envelope;
import art.arcane.react.api.web.dto.OnlinePlayerDto;
import art.arcane.react.api.web.dto.PlayerTeleportDto;
import art.arcane.react.api.web.resource.PlayerResource;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ConflictResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PlayerResourceTest {

    private static final UUID PLAYER_ID = UUID.fromString("12345678-1234-5678-9234-567812345678");

    private PairingToken adminToken;
    private PairingToken operatorToken;
    private PairingToken viewerToken;
    private FakePlayerBackend backend;
    private AtomicReference<WebMutation> mutation;
    private PlayerResource resource;

    @BeforeEach
    void setUp() {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        TokenRecord adminRecord = new TokenRecord(
            "tok-player-admin",
            "admin-device",
            1000L,
            Set.of("read", "op:execute", "admin"),
            "admin"
        );
        TokenRecord operatorRecord = new TokenRecord(
            "tok-player-op",
            "operator-device",
            1000L,
            Set.of("read", "op:execute"),
            "operator"
        );
        TokenRecord viewerRecord = new TokenRecord(
            "tok-player-viewer",
            "viewer-device",
            1000L,
            Set.of("read"),
            "viewer"
        );
        TokenStore store = TokenStore.inMemory(adminRecord, operatorRecord, viewerRecord);
        adminToken = verified(secret, adminRecord, store);
        operatorToken = verified(secret, operatorRecord, store);
        viewerToken = verified(secret, viewerRecord, store);
        backend = new FakePlayerBackend();
        mutation = new AtomicReference<>();
        resource = new PlayerResource(backend, (context, reported) -> mutation.set(reported));
    }

    @Test
    void adminListsOnlinePlayers() {
        Context context = mock(Context.class);
        when(context.<PairingToken>attribute("token")).thenReturn(adminToken);
        ArgumentCaptor<Object> response = ArgumentCaptor.forClass(Object.class);

        resource.list(context);

        verify(context).json(response.capture());
        PlayerResource.ListResponse list = (PlayerResource.ListResponse) response.getValue();
        assertEquals(1, list.data().length);
        assertEquals(PLAYER_ID.toString(), list.data()[0].id());
    }

    @Test
    void viewerCannotListPlayersOrReachBackend() {
        Context context = mock(Context.class);
        when(context.<PairingToken>attribute("token")).thenReturn(viewerToken);

        assertThrows(ForbiddenResponse.class, () -> resource.list(context));
        assertEquals(0, backend.listCalls);
    }

    @Test
    void operatorCannotListOrTeleportPlayers() {
        Context listContext = mock(Context.class);
        when(listContext.<PairingToken>attribute("token")).thenReturn(operatorToken);
        assertThrows(ForbiddenResponse.class, () -> resource.list(listContext));

        Context teleportContext = mock(Context.class);
        when(teleportContext.<PairingToken>attribute("token")).thenReturn(operatorToken);
        assertThrows(ForbiddenResponse.class, () -> resource.teleport(teleportContext));
        verify(teleportContext, never()).bodyAsClass(Map.class);
        assertEquals(0, backend.listCalls);
        assertEquals(0, backend.queueCalls);
    }

    @Test
    void adminQueuesExactIntegralTargetAndReportsMutation() {
        Context context = teleportContext(adminToken, Map.of(
            "worldKey", "minecraft:overworld",
            "blockX", -24,
            "blockZ", 40,
            "confirm", true
        ));
        when(context.status(202)).thenReturn(context);
        ArgumentCaptor<Object> response = ArgumentCaptor.forClass(Object.class);

        resource.teleport(context);

        assertEquals(PLAYER_ID, backend.playerId);
        assertEquals("minecraft:overworld", backend.worldKey);
        assertEquals(-24, backend.blockX);
        assertEquals(40, backend.blockZ);
        verify(context).status(202);
        verify(context).json(response.capture());
        @SuppressWarnings("unchecked")
        Envelope<PlayerTeleportDto> envelope = (Envelope<PlayerTeleportDto>) response.getValue();
        assertEquals("queued", envelope.data().status());
        assertEquals("Alice", envelope.data().playerName());
        assertEquals(-24, envelope.data().blockX());
        assertEquals("player.teleport", mutation.get().operation());
        assertTrue(mutation.get().detail().contains("x=-24 z=40"));
    }

    @Test
    void malformedUuidFractionalAndOutOfRangeCoordinatesAreRejected() {
        Context malformedId = teleportContext(adminToken, Map.of(
            "worldKey", "minecraft:overworld",
            "blockX", 0,
            "blockZ", 0,
            "confirm", true
        ));
        when(malformedId.pathParam("id")).thenReturn("not-a-uuid");
        assertThrows(BadRequestResponse.class, () -> resource.teleport(malformedId));

        Context fractional = teleportContext(adminToken, Map.of(
            "worldKey", "minecraft:overworld",
            "blockX", 1.5D,
            "blockZ", 0,
            "confirm", true
        ));
        assertThrows(BadRequestResponse.class, () -> resource.teleport(fractional));

        Context outOfRange = teleportContext(adminToken, Map.of(
            "worldKey", "minecraft:overworld",
            "blockX", PlayerResource.MAX_BLOCK_COORDINATE + 1L,
            "blockZ", 0,
            "confirm", true
        ));
        assertThrows(BadRequestResponse.class, () -> resource.teleport(outOfRange));

        Context unconfirmed = teleportContext(adminToken, Map.of(
            "worldKey", "minecraft:overworld",
            "blockX", 0,
            "blockZ", 0
        ));
        assertThrows(BadRequestResponse.class, () -> resource.teleport(unconfirmed));
        assertEquals(0, backend.queueCalls);
    }

    @Test
    void offlineAndOutsideBorderTargetsAreRejectedWithoutAudit() {
        backend.nextStatus = PlayerBackend.TeleportStatus.PLAYER_OFFLINE;
        assertThrows(
            NotFoundResponse.class,
            () -> resource.teleport(teleportContext(adminToken, validBody()))
        );
        assertEquals(null, mutation.get());

        backend.nextStatus = PlayerBackend.TeleportStatus.OUTSIDE_BORDER;
        assertThrows(
            ConflictResponse.class,
            () -> resource.teleport(teleportContext(adminToken, validBody()))
        );
        assertEquals(null, mutation.get());
    }

    @Test
    void viewerAuthorizationRunsBeforeBodyOrBackendWork() {
        Context context = mock(Context.class);
        when(context.<PairingToken>attribute("token")).thenReturn(viewerToken);

        assertThrows(ForbiddenResponse.class, () -> resource.teleport(context));
        verify(context, never()).bodyAsClass(Map.class);
        assertEquals(0, backend.queueCalls);
    }

    private static Map<String, Object> validBody() {
        return Map.of(
            "worldKey", "minecraft:overworld",
            "blockX", 8,
            "blockZ", 8,
            "confirm", true
        );
    }

    private static Context teleportContext(PairingToken token, Map<String, Object> body) {
        Context context = mock(Context.class);
        when(context.<PairingToken>attribute("token")).thenReturn(token);
        when(context.pathParam("id")).thenReturn(PLAYER_ID.toString());
        when(context.bodyAsClass(Map.class)).thenReturn(body);
        return context;
    }

    private static PairingToken verified(byte[] secret, TokenRecord record, TokenStore store) {
        String bearer = PairingToken.mint(
            secret,
            record.id(),
            record.label(),
            record.issuedAt(),
            record.scopes()
        );
        return PairingToken.verify(secret, bearer, store).orElseThrow();
    }

    private static final class FakePlayerBackend implements PlayerBackend {

        private int listCalls;
        private int queueCalls;
        private UUID playerId;
        private String worldKey;
        private int blockX;
        private int blockZ;
        private TeleportStatus nextStatus = TeleportStatus.QUEUED;

        @Override
        public List<OnlinePlayerDto> list() {
            listCalls++;
            return List.of(new OnlinePlayerDto(PLAYER_ID.toString(), "Alice"));
        }

        @Override
        public TeleportResult queueTeleport(
            UUID requestedPlayerId,
            String requestedWorldKey,
            int requestedBlockX,
            int requestedBlockZ
        ) {
            queueCalls++;
            playerId = requestedPlayerId;
            worldKey = requestedWorldKey;
            blockX = requestedBlockX;
            blockZ = requestedBlockZ;
            return new TeleportResult(nextStatus, "Alice");
        }
    }
}
