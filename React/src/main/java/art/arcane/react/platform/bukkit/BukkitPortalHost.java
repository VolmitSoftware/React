package art.arcane.react.platform.bukkit;

import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.PortalHost;
import art.arcane.react.platform.Subscription;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;

import java.util.function.Consumer;

public class BukkitPortalHost implements PortalHost {
  @Override
  public Subscription onPortal(ListenerPriority priority, boolean ignoreCancelled, Consumer<Portal> listener) {
    Subscription players = BukkitEvents.subscribe(PlayerPortalEvent.class, priority, ignoreCancelled,
        event -> listener.accept(new BukkitPortal(event.getPlayer(), event)));
    Subscription entities = BukkitEvents.subscribe(EntityPortalEvent.class, priority, ignoreCancelled,
        event -> listener.accept(new BukkitPortal(event.getEntity(), event)));
    return () -> {
      players.cancel();
      entities.cancel();
    };
  }

  private record BukkitPortal(Entity traveller, Location source, Location target, Cancellable event) implements Portal {
    private BukkitPortal(Player player, PlayerPortalEvent event) {
      this(player, event.getFrom(), event.getTo(), event);
    }

    private BukkitPortal(Entity entity, EntityPortalEvent event) {
      this(entity, event.getFrom(), event.getTo(), event);
    }

    @Override
    public GameEntity entity() {
      return new BukkitGameEntity(traveller);
    }

    @Override
    public boolean isPlayer() {
      return traveller instanceof Player;
    }

    @Override
    public GameLocation from() {
      return BukkitGameEntity.location(source);
    }

    @Override
    public GameLocation to() {
      return BukkitGameEntity.location(target);
    }

    @Override
    public boolean isCancelled() {
      return event.isCancelled();
    }

    @Override
    public void cancel() {
      event.setCancelled(true);
    }
  }
}
