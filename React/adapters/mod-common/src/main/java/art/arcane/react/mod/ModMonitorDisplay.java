package art.arcane.react.mod;

import art.arcane.react.api.monitor.MonitorDisplay;
import art.arcane.react.platform.Subscription;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.Duration;
import java.util.UUID;

/**
 * The action bar monitor on mods, sent as vanilla action bar and title packets. React is the only title user here,
 * so the focus claim is always granted. React's async pool ticks it, as React's ticker does on Bukkit.
 */
final class ModMonitorDisplay implements MonitorDisplay {
    private static final int CLEAR_DELAY_TICKS = 3;
    private static final FocusClaim ALWAYS = new FocusClaim() {
        @Override
        public boolean resolve() {
            return true;
        }

        @Override
        public boolean granted() {
            return true;
        }

        @Override
        public void release() {
        }
    };

    private final MinecraftServer server;
    private final ModTicks ticks;
    private final ModComponentHost scheduler;

    ModMonitorDisplay(MinecraftServer server, ModTicks ticks, ModComponentHost scheduler) {
        this.server = server;
        this.ticks = ticks;
        this.scheduler = scheduler;
    }

    @Override
    public Subscription schedule(String id, Runnable tick) {
        return scheduler.schedule(this, id, 50L, tick);
    }

    @Override
    public void render(UUID viewerId, Runnable render) {
        if (player(viewerId) != null) {
            render.run();
        }
    }

    @Override
    public FocusClaim claimFocus(UUID viewerId) {
        return ALWAYS;
    }

    @Override
    public void actionBar(UUID viewerId, String legacyHeader) {
        send(viewerId, new ClientboundSetActionBarTextPacket(ModLegacyText.component(legacyHeader)));
    }

    @Override
    public void titleTimes(UUID viewerId, Duration fadeIn, Duration stay, Duration fadeOut) {
        send(viewerId, new ClientboundSetTitlesAnimationPacket(ticks(fadeIn), ticks(stay), ticks(fadeOut)));
    }

    @Override
    public void title(UUID viewerId, net.kyori.adventure.text.Component title) {
        send(viewerId, new ClientboundSetTitleTextPacket(ModText.component(title)));
    }

    @Override
    public void subtitle(UUID viewerId, net.kyori.adventure.text.Component subtitle) {
        send(viewerId, new ClientboundSetSubtitleTextPacket(ModText.component(subtitle)));
    }

    @Override
    public void clearTitle(UUID viewerId) {
        send(viewerId, new ClientboundClearTitlesPacket(false));
    }

    @Override
    public void clear(UUID viewerId, boolean blankTitle) {
        ticks.later(() -> {
            send(viewerId, new ClientboundSetActionBarTextPacket(Component.empty()));
            if (blankTitle) {
                send(viewerId, new ClientboundSetTitleTextPacket(Component.literal(" ")));
                send(viewerId, new ClientboundSetSubtitleTextPacket(Component.literal(" ")));
            }
        }, CLEAR_DELAY_TICKS);
    }

    private static int ticks(Duration duration) {
        return (int) Math.max(0L, duration.toMillis() / 50L);
    }

    private void send(UUID viewerId, Packet<?> packet) {
        ServerPlayer player = player(viewerId);
        if (player != null) {
            player.connection.send(packet);
        }
    }

    private ServerPlayer player(UUID viewerId) {
        return viewerId == null ? null : server.getPlayerList().getPlayer(viewerId);
    }
}
