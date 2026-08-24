package art.arcane.react.nms.v26_2_R1;

import art.arcane.react.nms.ExplosionPacketSuppressor;
import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.CraftWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

class ExplosionPacketSubstitutionTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void clearHookAndScope() {
        ExplosionPacketSubstitution.configure(null);
        ExplosionPacketSubstitution.exitExplosion();
    }

    @Test
    void scopeBuffersAndSuppressesTheEntireRecipientFanoutOnce() {
        CraftWorld world = Mockito.mock(CraftWorld.class);
        ServerLevel level = Mockito.mock(ServerLevel.class);
        ServerPlayer player = Mockito.mock(ServerPlayer.class);
        ServerGamePacketListenerImpl firstConnection = connection(player);
        ServerGamePacketListenerImpl secondConnection = connection(player);
        ClientboundExplodePacket firstPacket = packet(Optional.empty());
        ClientboundExplodePacket secondPacket = packet(Optional.empty());
        ExplosionPacketSuppressor suppressor = Mockito.mock(ExplosionPacketSuppressor.class);
        Mockito.when(player.level()).thenReturn(level);
        Mockito.when(level.getWorld()).thenReturn(world);
        Mockito.when(suppressor.shouldSuppress(world, 1D, 2D, 3D, 4F, 2)).thenReturn(true);
        ExplosionPacketSubstitution.configure(suppressor);

        ExplosionPacketSubstitution.enterExplosion();
        ExplosionPacketSubstitution.maybeSend(firstConnection, firstPacket);
        ExplosionPacketSubstitution.maybeSend(secondConnection, secondPacket);

        Mockito.verify(firstConnection, Mockito.never()).send(firstPacket);
        Mockito.verify(secondConnection, Mockito.never()).send(secondPacket);

        ExplosionPacketSubstitution.exitExplosion();

        Mockito.verify(suppressor).shouldSuppress(world, 1D, 2D, 3D, 4F, 2);
        Mockito.verify(firstConnection, Mockito.never()).send(firstPacket);
        Mockito.verify(secondConnection, Mockito.never()).send(secondPacket);
    }

    @Test
    void oneKnockbackPacketPreservesTheEntireFanoutWithoutMarkingSuppression() {
        ServerPlayer player = Mockito.mock(ServerPlayer.class);
        ServerGamePacketListenerImpl firstConnection = connection(player);
        ServerGamePacketListenerImpl secondConnection = connection(player);
        ClientboundExplodePacket firstPacket = packet(Optional.empty());
        ClientboundExplodePacket knockbackPacket = packet(Optional.of(new Vec3(0.1D, 0.2D, 0.3D)));
        ExplosionPacketSuppressor suppressor = Mockito.mock(ExplosionPacketSuppressor.class);
        ExplosionPacketSubstitution.configure(suppressor);

        ExplosionPacketSubstitution.enterExplosion();
        ExplosionPacketSubstitution.maybeSend(firstConnection, firstPacket);
        ExplosionPacketSubstitution.maybeSend(secondConnection, knockbackPacket);
        ExplosionPacketSubstitution.exitExplosion();

        Mockito.verifyNoInteractions(suppressor);
        Mockito.verify(firstConnection).send(firstPacket);
        Mockito.verify(secondConnection).send(knockbackPacket);
    }

    @Test
    void injectedRuntimeHasNoPluginPrivateNestMembers() {
        Assertions.assertEquals(0, ExplosionPacketSubstitution.class.getDeclaredClasses().length);
    }

    private ServerGamePacketListenerImpl connection(ServerPlayer player) {
        ServerGamePacketListenerImpl connection = Mockito.mock(ServerGamePacketListenerImpl.class);
        connection.player = player;
        return connection;
    }

    private ClientboundExplodePacket packet(Optional<Vec3> knockback) {
        ClientboundExplodePacket packet = Mockito.mock(ClientboundExplodePacket.class);
        Mockito.when(packet.center()).thenReturn(new Vec3(1D, 2D, 3D));
        Mockito.when(packet.radius()).thenReturn(4F);
        Mockito.when(packet.playerKnockback()).thenReturn(knockback);
        return packet;
    }
}
