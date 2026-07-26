package art.arcane.react.core.bridge;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public final class NmsAccessors {
    public static final String WORLD_HANDLE = "CraftWorld.getHandle";
    public static final String ENTITY_HANDLE = "CraftEntity.getHandle";
    public static final String PLAYER_CONNECTION = "ServerPlayer.connection";
    public static final String CONNECTION_SEND = "PacketListener.send";
    public static final String COLLECT_PACKET = "ClientboundTakeItemEntityPacket.constructor";

    private static final int MAX_REPORTED_MEMBERS = 24;
    private static final NmsAccessors UNRESOLVED = new NmsAccessors(Map.of());

    private final NmsBridgeHandle worldHandleAccessor;
    private final NmsBridgeHandle entityHandleAccessor;
    private final NmsBridgeHandle playerConnectionField;
    private final NmsBridgeHandle connectionSendMethod;
    private final NmsBridgeHandle collectPacketConstructor;

    private NmsAccessors(Map<String, NmsBridgeHandle> resolved) {
        this.worldHandleAccessor = handleOrMissing(resolved, WORLD_HANDLE, BridgeKind.METHOD);
        this.entityHandleAccessor = handleOrMissing(resolved, ENTITY_HANDLE, BridgeKind.METHOD);
        this.playerConnectionField = handleOrMissing(resolved, PLAYER_CONNECTION, BridgeKind.FIELD);
        this.connectionSendMethod = handleOrMissing(resolved, CONNECTION_SEND, BridgeKind.METHOD);
        this.collectPacketConstructor = handleOrMissing(resolved, COLLECT_PACKET, BridgeKind.CONSTRUCTOR);
    }

    public static List<NmsBridgeDescriptor> defaultDescriptors() {
        return List.of(
                new NmsBridgeDescriptor(
                        WORLD_HANDLE, BridgeKind.METHOD,
                        List.of("org.bukkit.craftbukkit.CraftWorld"),
                        "getHandle",
                        List.of(List.of()),
                        "net.minecraft.server.level.ServerLevel",
                        Optional.empty()),
                new NmsBridgeDescriptor(
                        ENTITY_HANDLE, BridgeKind.METHOD,
                        List.of("org.bukkit.craftbukkit.entity.CraftEntity"),
                        "getHandle",
                        List.of(List.of()),
                        "net.minecraft.world.entity.Entity",
                        Optional.empty()),
                new NmsBridgeDescriptor(
                        PLAYER_CONNECTION, BridgeKind.FIELD,
                        List.of("net.minecraft.server.level.ServerPlayer"),
                        "connection",
                        List.of(),
                        "net.minecraft.server.network.ServerGamePacketListenerImpl",
                        Optional.empty()),
                new NmsBridgeDescriptor(
                        CONNECTION_SEND, BridgeKind.METHOD,
                        List.of("net.minecraft.server.network.ServerCommonPacketListenerImpl",
                                "net.minecraft.server.network.ServerGamePacketListenerImpl"),
                        "send",
                        List.of(List.of("net.minecraft.network.protocol.Packet")),
                        "void",
                        Optional.empty()),
                new NmsBridgeDescriptor(
                        COLLECT_PACKET, BridgeKind.CONSTRUCTOR,
                        List.of("net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket"),
                        "<init>",
                        List.of(List.of("int", "int", "int")),
                        "net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket",
                        Optional.empty()));
    }

    public static NmsAccessors unresolved() {
        return UNRESOLVED;
    }

    public static NmsAccessors resolve(NmsBridgeRegistry registry, List<NmsBridgeDescriptor> descriptors,
            Consumer<String> warningSink) {
        if (registry == null || descriptors == null || descriptors.isEmpty()) {
            return UNRESOLVED;
        }

        Map<String, NmsBridgeHandle> resolved = new HashMap<>(descriptors.size());
        for (NmsBridgeDescriptor descriptor : descriptors) {
            NmsBridgeHandle handle = registry.resolve(descriptor);
            resolved.put(descriptor.logicalId(), handle);
            if (!handle.available() && warningSink != null) {
                warningSink.accept(describeFailure(descriptor, handle.resolution().failureReason()));
            }
        }

        return new NmsAccessors(resolved);
    }

    public boolean worldHandleAvailable() {
        return worldHandleAccessor.available();
    }

    public boolean entityHandleAvailable() {
        return entityHandleAccessor.available();
    }

    public boolean playerConnectionAvailable() {
        return playerConnectionField.available();
    }

    public boolean connectionSendAvailable() {
        return connectionSendMethod.available();
    }

    public boolean collectPacketAvailable() {
        return collectPacketConstructor.available();
    }

    public Object worldHandle(Object craftWorld) {
        MethodHandle handle = worldHandleAccessor.methodHandle();
        try {
            return handle.invoke(craftWorld);
        } catch (Throwable throwable) {
            throw new BridgeInvocationException(WORLD_HANDLE, throwable);
        }
    }

    public Object entityHandle(Object craftEntity) {
        MethodHandle handle = entityHandleAccessor.methodHandle();
        try {
            return handle.invoke(craftEntity);
        } catch (Throwable throwable) {
            throw new BridgeInvocationException(ENTITY_HANDLE, throwable);
        }
    }

    public Object connection(Object serverPlayer) {
        return playerConnectionField.varHandle().get(serverPlayer);
    }

    public void send(Object connection, Object packet) {
        MethodHandle handle = connectionSendMethod.methodHandle();
        try {
            handle.invoke(connection, packet);
        } catch (Throwable throwable) {
            throw new BridgeInvocationException(CONNECTION_SEND, throwable);
        }
    }

    public Object collectPacket(int itemId, int playerId, int amount) {
        MethodHandle handle = collectPacketConstructor.methodHandle();
        try {
            return handle.invoke(itemId, playerId, amount);
        } catch (Throwable throwable) {
            throw new BridgeInvocationException(COLLECT_PACKET, throwable);
        }
    }

    private static NmsBridgeHandle handleOrMissing(Map<String, NmsBridgeHandle> resolved, String logicalId,
            BridgeKind kind) {
        NmsBridgeHandle handle = resolved.get(logicalId);
        if (handle != null) {
            return handle;
        }

        return new NmsBridgeHandle(new BridgeResolution(
                logicalId, kind, false, "", "", "resolution=unresolved",
                "No descriptor was supplied for " + logicalId));
    }

    private static String describeFailure(NmsBridgeDescriptor descriptor, String failureReason) {
        return "NMS accessor unavailable: " + descriptor.logicalId()
                + " — looked for " + descriptor.kind() + " " + descriptor.memberName()
                + describeSignature(descriptor)
                + " on " + String.join(" or ", descriptor.classNames())
                + "; resolver reported: " + failureReason
                + "; runtime declares: " + describeDeclaredMembers(descriptor);
    }

    private static String describeSignature(NmsBridgeDescriptor descriptor) {
        List<List<String>> candidates = descriptor.parameterTypeNames();
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }

        List<String> rendered = new ArrayList<>(candidates.size());
        for (List<String> candidate : candidates) {
            rendered.add("(" + String.join(", ", candidate) + ")");
        }
        return String.join(" or ", rendered);
    }

    private static String describeDeclaredMembers(NmsBridgeDescriptor descriptor) {
        for (String className : descriptor.classNames()) {
            Class<?> resolved = forName(className);
            if (resolved == null) {
                continue;
            }

            Set<String> members;
            try {
                members = collectMembers(resolved, descriptor.kind());
            } catch (Throwable throwable) {
                return className + " could not be inspected: " + throwable.getClass().getSimpleName();
            }

            if (members.isEmpty()) {
                return className + " declares no members of that kind";
            }
            return className + " declares " + String.join(", ", members);
        }

        return "none of those classes are present on this runtime";
    }

    private static Set<String> collectMembers(Class<?> type, BridgeKind kind) {
        Set<String> members = new LinkedHashSet<>();
        Class<?> search = type;
        while (search != null && search != Object.class && members.size() < MAX_REPORTED_MEMBERS) {
            switch (kind) {
                case FIELD, STATIC_FIELD -> {
                    for (Field field : search.getDeclaredFields()) {
                        members.add(field.getType().getSimpleName() + " " + field.getName());
                    }
                }
                case METHOD, STATIC_METHOD -> {
                    for (Method method : search.getDeclaredMethods()) {
                        members.add(method.getReturnType().getSimpleName() + " " + method.getName()
                                + "/" + method.getParameterCount());
                    }
                }
                case CONSTRUCTOR -> {
                    for (Constructor<?> constructor : search.getDeclaredConstructors()) {
                        members.add("<init>/" + constructor.getParameterCount());
                    }
                }
            }
            search = kind == BridgeKind.CONSTRUCTOR ? null : search.getSuperclass();
        }

        if (members.size() <= MAX_REPORTED_MEMBERS) {
            return members;
        }

        Set<String> trimmed = new LinkedHashSet<>();
        for (String member : members) {
            if (trimmed.size() >= MAX_REPORTED_MEMBERS) {
                trimmed.add("...");
                break;
            }
            trimmed.add(member);
        }
        return trimmed;
    }

    private static Class<?> forName(String className) {
        try {
            return Class.forName(className, false, NmsAccessors.class.getClassLoader());
        } catch (Throwable ignored) {
            return null;
        }
    }
}
