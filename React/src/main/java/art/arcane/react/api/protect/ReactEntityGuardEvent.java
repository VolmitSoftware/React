package art.arcane.react.api.protect;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

public final class ReactEntityGuardEvent extends Event implements Cancellable {
  private static final HandlerList HANDLERS = new HandlerList();

  private final Entity entity;
  private final ReactOperation operation;
  private boolean cancelled;

  public ReactEntityGuardEvent(Entity entity, ReactOperation operation, boolean async) {
    super(async);
    this.entity = Objects.requireNonNull(entity, "entity");
    this.operation = Objects.requireNonNull(operation, "operation");
  }

  public Entity getEntity() {
    return entity;
  }

  public ReactOperation getOperation() {
    return operation;
  }

  @Override
  public boolean isCancelled() {
    return cancelled;
  }

  @Override
  public void setCancelled(boolean cancel) {
    cancelled = cancel;
  }

  @Override
  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
