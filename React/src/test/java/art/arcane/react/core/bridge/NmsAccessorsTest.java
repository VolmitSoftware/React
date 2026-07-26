package art.arcane.react.core.bridge;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class NmsAccessorsTest {
    private NmsBridgeRegistry registry;
    private List<String> warnings;
    private Consumer<String> warningSink;

    @BeforeEach
    public void setup() {
        registry = new NmsBridgeRegistry();
        warnings = new ArrayList<>();
        warningSink = warnings::add;
    }

    @Test
    public void defaultDescriptors_resolveWorldHandleThroughGetHandleNotAHandleField() {
        NmsBridgeDescriptor worldHandle = descriptorFor(NmsAccessors.defaultDescriptors(), NmsAccessors.WORLD_HANDLE);

        Assertions.assertEquals(BridgeKind.METHOD, worldHandle.kind());
        Assertions.assertEquals("getHandle", worldHandle.memberName());
        Assertions.assertEquals(List.of("org.bukkit.craftbukkit.CraftWorld"), worldHandle.classNames());
        Assertions.assertNotEquals("handle", worldHandle.memberName());
    }

    @Test
    public void defaultDescriptors_resolvePlayerConnectionAsAFieldOnServerPlayer() {
        NmsBridgeDescriptor connection = descriptorFor(NmsAccessors.defaultDescriptors(), NmsAccessors.PLAYER_CONNECTION);

        Assertions.assertEquals(BridgeKind.FIELD, connection.kind());
        Assertions.assertEquals("connection", connection.memberName());
        Assertions.assertEquals(List.of("net.minecraft.server.level.ServerPlayer"), connection.classNames());
    }

    @Test
    public void defaultDescriptors_pinSendToTheExactSinglePacketSignature() {
        NmsBridgeDescriptor send = descriptorFor(NmsAccessors.defaultDescriptors(), NmsAccessors.CONNECTION_SEND);

        Assertions.assertEquals(BridgeKind.METHOD, send.kind());
        Assertions.assertEquals("send", send.memberName());
        Assertions.assertEquals(List.of(List.of("net.minecraft.network.protocol.Packet")), send.parameterTypeNames());
        Assertions.assertEquals("void", send.returnTypeName());
    }

    @Test
    public void defaultDescriptors_carryNoVersionedClassNames() {
        for (NmsBridgeDescriptor descriptor : NmsAccessors.defaultDescriptors()) {
            for (String className : descriptor.classNames()) {
                Assertions.assertFalse(className.matches(".*\\.v\\d+_\\d+_R\\d+\\..*"),
                        "Descriptor " + descriptor.logicalId() + " must not bind a versioned package: " + className);
            }
        }
    }

    @Test
    public void handleFieldLookupFailsOnACraftWorldShapedClassButGetHandleResolves() {
        NmsBridgeHandle brokenFieldLookup = registry.resolve(new NmsBridgeDescriptor(
                "broken.handle.field", BridgeKind.FIELD,
                List.of(StandInCraftWorld.class.getName()), "handle",
                List.of(), "java.lang.Object", Optional.empty()));
        NmsBridgeHandle accessorLookup = registry.resolve(new NmsBridgeDescriptor(
                NmsAccessors.WORLD_HANDLE, BridgeKind.METHOD,
                List.of(StandInCraftWorld.class.getName()), "getHandle",
                List.of(List.of()), StandInServerLevel.class.getName(), Optional.empty()));

        Assertions.assertFalse(brokenFieldLookup.available());
        Assertions.assertTrue(accessorLookup.available());
    }

    @Test
    public void resolvedAccessorsReadTheWorldAndEntityHandlesWithoutThrowing() {
        NmsAccessors accessors = NmsAccessors.resolve(registry, standInDescriptors(), warningSink);
        StandInCraftWorld world = new StandInCraftWorld();
        StandInCraftPlayer player = new StandInCraftPlayer();

        Assertions.assertTrue(accessors.worldHandleAvailable());
        Assertions.assertTrue(accessors.entityHandleAvailable());
        Assertions.assertSame(world.getHandle(), accessors.worldHandle(world));
        Assertions.assertSame(player.getHandle(), accessors.entityHandle(player));
        Assertions.assertTrue(warnings.isEmpty(), "Fully resolvable accessors must not warn: " + warnings);
    }

    @Test
    public void entityHandleDispatchesVirtuallyThroughTheBaseClassAccessor() {
        NmsAccessors accessors = NmsAccessors.resolve(registry, standInDescriptors(), warningSink);
        StandInCraftPlayer player = new StandInCraftPlayer();

        Object handle = accessors.entityHandle(player);

        Assertions.assertInstanceOf(StandInServerPlayer.class, handle);
    }

    @Test
    public void connectionIsReadFromThePublicFieldOnTheResolvedHandle() {
        NmsAccessors accessors = NmsAccessors.resolve(registry, standInDescriptors(), warningSink);
        StandInCraftPlayer player = new StandInCraftPlayer();

        Object handle = accessors.entityHandle(player);
        Object connection = accessors.connection(handle);

        Assertions.assertTrue(accessors.playerConnectionAvailable());
        Assertions.assertSame(((StandInServerPlayer) handle).connection, connection);
    }

    @Test
    public void sendResolvesTheVoidPacketMethodAndNotTheSyntheticLambda() {
        NmsAccessors accessors = NmsAccessors.resolve(registry, standInDescriptors(), warningSink);
        StandInCraftPlayer player = new StandInCraftPlayer();
        StandInPacket packet = new StandInPacket();

        Object connection = accessors.connection(accessors.entityHandle(player));
        accessors.send(connection, packet);

        Assertions.assertEquals(List.of(packet), ((StandInPacketListener) connection).sent);
    }

    @Test
    public void parameterOnlyMethodMatchingIsAmbiguousOnAPacketListenerShapedClass() {
        Class<?>[] singlePacket = new Class<?>[]{StandInPacket.class};
        int matches = 0;
        for (Method method : StandInPacketListener.class.getDeclaredMethods()) {
            if (Arrays.equals(method.getParameterTypes(), singlePacket)) {
                matches++;
            }
        }

        Assertions.assertTrue(matches > 1,
                "A packet-listener shaped class exposes more than one (Packet) method, so accessors must pin name plus signature");
    }

    @Test
    public void collectPacketIsBuiltThroughTheResolvedConstructor() {
        NmsAccessors accessors = NmsAccessors.resolve(registry, standInDescriptors(), warningSink);

        Object packet = accessors.collectPacket(7, 9, 3);

        Assertions.assertTrue(accessors.collectPacketAvailable());
        Assertions.assertInstanceOf(StandInCollectPacket.class, packet);
        Assertions.assertEquals(7, ((StandInCollectPacket) packet).itemId);
        Assertions.assertEquals(9, ((StandInCollectPacket) packet).playerId);
        Assertions.assertEquals(3, ((StandInCollectPacket) packet).amount);
    }

    @Test
    public void accessorsKeepWorkingAfterTheRegistryIsCleared() {
        NmsAccessors accessors = NmsAccessors.resolve(registry, standInDescriptors(), warningSink);
        StandInCraftWorld world = new StandInCraftWorld();

        registry.clear();

        Assertions.assertTrue(accessors.worldHandleAvailable());
        for (int i = 0; i < 1000; i++) {
            Assertions.assertSame(world.getHandle(), accessors.worldHandle(world));
        }
        Assertions.assertTrue(warnings.isEmpty(), "Cached accessors must not re-resolve or warn: " + warnings);
    }

    @Test
    public void unresolvableAccessorWarnsExactlyOnceWithTheLookupAndTheFindings() {
        List<NmsBridgeDescriptor> descriptors = new ArrayList<>(standInDescriptors());
        descriptors.set(0, new NmsBridgeDescriptor(
                NmsAccessors.WORLD_HANDLE, BridgeKind.FIELD,
                List.of(StandInCraftWorld.class.getName()), "handle",
                List.of(), StandInServerLevel.class.getName(), Optional.empty()));

        NmsAccessors accessors = NmsAccessors.resolve(registry, descriptors, warningSink);

        Assertions.assertFalse(accessors.worldHandleAvailable());
        Assertions.assertEquals(1, warnings.size(), "Expected exactly one warning, got " + warnings);
        String warning = warnings.get(0);
        Assertions.assertTrue(warning.contains(NmsAccessors.WORLD_HANDLE), warning);
        Assertions.assertTrue(warning.contains("handle"), warning);
        Assertions.assertTrue(warning.contains(StandInCraftWorld.class.getName()), warning);
        Assertions.assertTrue(warning.contains("world"), "Warning must report what the runtime actually declares: " + warning);
    }

    @Test
    public void unresolvableAccessorDoesNotThrowPerCallAndNeverWarnsAgain() {
        List<NmsBridgeDescriptor> descriptors = new ArrayList<>(standInDescriptors());
        descriptors.set(0, new NmsBridgeDescriptor(
                NmsAccessors.WORLD_HANDLE, BridgeKind.FIELD,
                List.of(StandInCraftWorld.class.getName()), "handle",
                List.of(), StandInServerLevel.class.getName(), Optional.empty()));
        NmsAccessors accessors = NmsAccessors.resolve(registry, descriptors, warningSink);

        for (int i = 0; i < 1000; i++) {
            Assertions.assertFalse(accessors.worldHandleAvailable());
        }

        Assertions.assertEquals(1, warnings.size(), "Availability checks must not re-warn: " + warnings);
        Assertions.assertThrows(BridgeUnavailableException.class, () -> accessors.worldHandle(new StandInCraftWorld()));
    }

    @Test
    public void missingRegistryYieldsUnresolvedAccessorsInsteadOfThrowing() {
        NmsAccessors accessors = NmsAccessors.resolve(null, NmsAccessors.defaultDescriptors(), warningSink);

        Assertions.assertSame(NmsAccessors.unresolved(), accessors);
        Assertions.assertFalse(accessors.worldHandleAvailable());
        Assertions.assertFalse(accessors.entityHandleAvailable());
        Assertions.assertFalse(accessors.playerConnectionAvailable());
        Assertions.assertFalse(accessors.connectionSendAvailable());
        Assertions.assertFalse(accessors.collectPacketAvailable());
        Assertions.assertTrue(warnings.isEmpty());
    }

    private static NmsBridgeDescriptor descriptorFor(List<NmsBridgeDescriptor> descriptors, String logicalId) {
        for (NmsBridgeDescriptor descriptor : descriptors) {
            if (descriptor.logicalId().equals(logicalId)) {
                return descriptor;
            }
        }
        throw new AssertionError("No descriptor declared for " + logicalId);
    }

    private static List<NmsBridgeDescriptor> standInDescriptors() {
        return List.of(
                new NmsBridgeDescriptor(
                        NmsAccessors.WORLD_HANDLE, BridgeKind.METHOD,
                        List.of(StandInCraftWorld.class.getName()), "getHandle",
                        List.of(List.of()), StandInServerLevel.class.getName(), Optional.empty()),
                new NmsBridgeDescriptor(
                        NmsAccessors.ENTITY_HANDLE, BridgeKind.METHOD,
                        List.of(StandInCraftEntity.class.getName()), "getHandle",
                        List.of(List.of()), StandInEntity.class.getName(), Optional.empty()),
                new NmsBridgeDescriptor(
                        NmsAccessors.PLAYER_CONNECTION, BridgeKind.FIELD,
                        List.of(StandInServerPlayer.class.getName()), "connection",
                        List.of(), StandInPacketListener.class.getName(), Optional.empty()),
                new NmsBridgeDescriptor(
                        NmsAccessors.CONNECTION_SEND, BridgeKind.METHOD,
                        List.of(StandInPacketListener.class.getName()), "send",
                        List.of(List.of(StandInPacket.class.getName())), "void", Optional.empty()),
                new NmsBridgeDescriptor(
                        NmsAccessors.COLLECT_PACKET, BridgeKind.CONSTRUCTOR,
                        List.of(StandInCollectPacket.class.getName()), "<init>",
                        List.of(List.of("int", "int", "int")), StandInCollectPacket.class.getName(), Optional.empty()));
    }

    public static class StandInPacket {
    }

    public static class StandInCollectPacket extends StandInPacket {
        public final int itemId;
        public final int playerId;
        public final int amount;

        public StandInCollectPacket(int itemId, int playerId, int amount) {
            this.itemId = itemId;
            this.playerId = playerId;
            this.amount = amount;
        }
    }

    public static class StandInPacketListener {
        public final List<Object> sent = new ArrayList<>();

        public void send(StandInPacket packet) {
            sent.add(packet);
        }

        public void send(StandInPacket packet, Object listener) {
            sent.add(packet);
            sent.add(listener);
        }

        private static String lambda$send$0(StandInPacket packet) {
            return String.valueOf(packet);
        }
    }

    public static class StandInEntity {
    }

    public static class StandInServerPlayer extends StandInEntity {
        public StandInPacketListener connection = new StandInPacketListener();
    }

    public static class StandInCraftEntity {
        protected StandInEntity entity = new StandInEntity();

        public StandInEntity getHandle() {
            return entity;
        }
    }

    public static class StandInCraftLivingEntity extends StandInCraftEntity {
    }

    public static class StandInCraftPlayer extends StandInCraftLivingEntity {
        private final StandInServerPlayer serverPlayer = new StandInServerPlayer();

        @Override
        public StandInServerPlayer getHandle() {
            return serverPlayer;
        }
    }

    public static class StandInRegionAccessor {
    }

    public static class StandInServerLevel {
    }

    public static class StandInCraftWorld extends StandInRegionAccessor {
        private final StandInServerLevel world = new StandInServerLevel();

        public StandInServerLevel getHandle() {
            return world;
        }
    }
}
