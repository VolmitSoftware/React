package art.arcane.react.nms.v26_2_R1;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;

class PluginChunkTicketCountTest {
    private NmsBridgeImpl bridge;
    private CraftWorld world;
    private ChunkHolderManager manager;
    private Long2ObjectOpenHashMap<Collection<Ticket>> tickets;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        bridge = new NmsBridgeImpl();
        world = Mockito.mock(CraftWorld.class);
        manager = Mockito.mock(ChunkHolderManager.class);
        tickets = new Long2ObjectOpenHashMap<>();
        ServerLevel level = Mockito.mock(ServerLevel.class);
        ChunkTaskScheduler scheduler = Mockito.mock(ChunkTaskScheduler.class);
        Field managerField = ChunkTaskScheduler.class.getField("chunkHolderManager");
        managerField.setAccessible(true);
        managerField.set(scheduler, manager);
        Mockito.when(world.getHandle()).thenReturn(level);
        Mockito.when(level.moonrise$getChunkTaskScheduler()).thenReturn(scheduler);
        Mockito.when(manager.getTicketsCopy()).thenReturn(tickets);
    }

    @AfterEach
    void neverLoadsChunks() {
        Mockito.verify(world, Mockito.never()).getChunkAt(Mockito.anyInt(), Mockito.anyInt());
        Mockito.verify(world, Mockito.never()).getChunkAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean());
        Mockito.verify(world, Mockito.never()).getPluginChunkTickets();
        Mockito.verify(world, Mockito.never()).getLoadedChunks();
    }

    @Test
    void emptySnapshotHasNoPluginTickets() {
        Assertions.assertEquals(0L, bridge.countPluginChunkTickets(world));
    }

    @Test
    void excludesForceLoadedPlayerAndGenericPluginTickets() {
        Plugin plugin = Mockito.mock(Plugin.class);
        tickets.put(CoordinateUtils.getChunkKey(7, -4), List.of(
                new Ticket<>(TicketType.FORCED, 31),
                new Ticket<>(TicketType.PLAYER_LOADING, 31),
                new Ticket<>(TicketType.PLUGIN, 31),
                new Ticket<>(TicketType.PLUGIN_TICKET, 31, plugin)
        ));

        Assertions.assertEquals(1L, bridge.countPluginChunkTickets(world));
    }

    @Test
    void countsEachPluginMembershipAcrossSharedAndSeparateChunks() {
        Plugin first = Mockito.mock(Plugin.class);
        Plugin second = Mockito.mock(Plugin.class);
        tickets.put(CoordinateUtils.getChunkKey(0, 0), List.of(
                new Ticket<>(TicketType.PLUGIN_TICKET, 31, first),
                new Ticket<>(TicketType.PLUGIN_TICKET, 31, second)
        ));
        tickets.put(CoordinateUtils.getChunkKey(4096, -4096), List.of(new Ticket<>(TicketType.PLUGIN_TICKET, 31, first)));
        tickets.put(CoordinateUtils.getChunkKey(-4096, 4096), List.of(new Ticket<>(TicketType.FORCED, 31)));

        Assertions.assertEquals(3L, bridge.countPluginChunkTickets(world));
    }

    @Test
    void snapshotFailureRemainsVisibleToTheSampler() {
        RuntimeException failure = new IllegalStateException("ticket snapshot failed");
        Mockito.when(manager.getTicketsCopy()).thenThrow(failure);

        Assertions.assertSame(failure,
                Assertions.assertThrows(IllegalStateException.class, () -> bridge.countPluginChunkTickets(world)));
    }
}
