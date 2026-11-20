package art.arcane.react.mod;

import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RuntimeMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * React's entity removal on mods, as on Bukkit: a red countdown over the entity, then a white flash, the soul escape
 * sound and the removal. Entities that already had a name keep it during the countdown.
 */
final class ModEntityKiller {
    private static final int SECONDS = 8;
    private static final int TICKS_PER_SECOND = 20;
    private final Map<UUID, Countdown> active = new ConcurrentHashMap<>();
    private int tick;

    private static final class Countdown {
        private final Entity entity;
        private final boolean hadName;
        private final Component originalName;
        private final boolean originalVisible;
        private int seconds = SECONDS;

        private Countdown(Entity entity) {
            this.entity = entity;
            this.hadName = entity.hasCustomName();
            this.originalName = entity.getCustomName();
            this.originalVisible = entity.isCustomNameVisible();
        }
    }

    void kill(Entity entity) {
        if (entity == null || entity.isRemoved()) {
            return;
        }
        active.putIfAbsent(entity.getUUID(), new Countdown(entity));
    }

    boolean isCountingDown(Entity entity) {
        Countdown countdown = active.get(entity.getUUID());
        return countdown != null && !countdown.hadName;
    }

    void tick() {
        if (active.isEmpty() || ++tick % TICKS_PER_SECOND != 0) {
            return;
        }
        List<Countdown> due = new ArrayList<>(active.values());
        for (Countdown countdown : due) {
            Entity entity = countdown.entity;
            if (entity.isRemoved() || !entity.isAlive()) {
                active.remove(entity.getUUID(), countdown);
                continue;
            }
            countdown.seconds--;
            if (countdown.seconds <= 0) {
                active.remove(entity.getUUID(), countdown);
                remove(entity);
                continue;
            }
            if (!countdown.hadName) {
                entity.setCustomName(Component.literal(ReactLanguage.plain(RuntimeMessages.ENTITY_KILLER_COUNTDOWN,
                        MessageArgument.untrusted("seconds", countdown.seconds))).withStyle(ChatFormatting.RED));
                entity.setCustomNameVisible(true);
            }
        }
    }

    void stopAll() {
        for (Countdown countdown : active.values()) {
            if (!countdown.hadName && !countdown.entity.isRemoved()) {
                countdown.entity.setCustomName(countdown.originalName);
                countdown.entity.setCustomNameVisible(countdown.originalVisible);
            }
        }
        active.clear();
    }

    private static void remove(Entity entity) {
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 1F, 1F, 1F), entity.getX(), entity.getY(),
                    entity.getZ(), 1, 0D, 0D, 0D, 0D);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.NEUTRAL, 0.5F, 0.9F);
        }
        entity.discard();
    }
}
