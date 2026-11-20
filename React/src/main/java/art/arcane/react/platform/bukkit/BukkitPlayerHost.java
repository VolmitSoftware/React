package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.PlayerHost;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.Subscription;
import art.arcane.volmlib.util.event.ProtectionProbe;
import art.arcane.volmlib.util.scheduling.EntityTeleports;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public final class BukkitPlayerHost implements PlayerHost, Listener {
  private static final Method GET_SEND_VIEW_DISTANCE = method("getSendViewDistance");
  private static final Method SET_SEND_VIEW_DISTANCE = method("setSendViewDistance", int.class);
  private final List<Listener<Move>> moves = new CopyOnWriteArrayList<>();
  private final List<Listener<Teleport>> teleports = new CopyOnWriteArrayList<>();
  private final List<Listener<Portal>> portals = new CopyOnWriteArrayList<>();
  private final List<Listener<ReactPlayer>> joins = new CopyOnWriteArrayList<>();
  private final List<Listener<ReactPlayer>> quits = new CopyOnWriteArrayList<>();
  private final List<Listener<PlayerHost.Cancellable>> interactions = new CopyOnWriteArrayList<>();
  private final List<Listener<PlayerHost.Cancellable>> attacks = new CopyOnWriteArrayList<>();
  private final List<Listener<PlayerHost.Cancellable>> consumes = new CopyOnWriteArrayList<>();
  private int subscribers;
  private boolean registered;

  @Override
  public List<ReactPlayer> players() {
    EntityController controller = React.controller(EntityController.class);
    Player[] players = controller == null ? null : controller.getFoliaPlayers();
    if (players == null) {
      return List.of();
    }
    List<ReactPlayer> result = new ArrayList<>(players.length);
    for (Player player : players) {
      if (player != null) {
        result.add(new BukkitReactPlayer(player));
      }
    }
    return result;
  }

  @Override
  public boolean isOnline(ReactPlayer player) {
    return player != null && player.handle() instanceof Player bukkit && bukkit.isOnline();
  }

  @Override
  public Subscription onMove(Listener<Move> listener) {
    return subscribe(moves, listener);
  }

  @Override
  public Subscription onTeleport(Listener<Teleport> listener) {
    return subscribe(teleports, listener);
  }

  @Override
  public Subscription onPortal(Listener<Portal> listener) {
    return subscribe(portals, listener);
  }

  @Override
  public Subscription onJoin(Listener<ReactPlayer> listener) {
    return subscribe(joins, listener);
  }

  @Override
  public Subscription onQuit(Listener<ReactPlayer> listener) {
    return subscribe(quits, listener);
  }

  @Override
  public Subscription onInteract(Listener<PlayerHost.Cancellable> listener) {
    return subscribe(interactions, listener);
  }

  @Override
  public Subscription onAttack(Listener<PlayerHost.Cancellable> listener) {
    return subscribe(attacks, listener);
  }

  @Override
  public Subscription onConsume(Listener<PlayerHost.Cancellable> listener) {
    return subscribe(consumes, listener);
  }

  @Override
  public boolean supportsSendViewDistance() {
    return GET_SEND_VIEW_DISTANCE != null && SET_SEND_VIEW_DISTANCE != null;
  }

  @Override
  public int sendViewDistance(ReactPlayer player) {
    if (GET_SEND_VIEW_DISTANCE == null) {
      throw new IllegalStateException("Per-player send view distance is not available");
    }
    return (int) invoke(GET_SEND_VIEW_DISTANCE, player);
  }

  @Override
  public void setSendViewDistance(ReactPlayer player, int distance) {
    if (SET_SEND_VIEW_DISTANCE == null) {
      throw new IllegalStateException("Per-player send view distance is not available");
    }
    invoke(SET_SEND_VIEW_DISTANCE, player, distance);
  }

  @Override
  public boolean isChunkGenerated(GameLocation location) {
    World world = location == null || location.worldId() == null ? null : org.bukkit.Bukkit.getWorld(location.worldId());
    return world == null || world.isChunkGenerated(location.chunkX(), location.chunkZ());
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(PlayerMoveEvent event) {
    if (moves.isEmpty()) {
      return;
    }
    BukkitMove move = new BukkitMove(event);
    for (Listener<Move> listener : moves) {
      listener.on(move);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(PlayerTeleportEvent event) {
    if (teleports.isEmpty()) {
      return;
    }
    BukkitTeleport teleport = new BukkitTeleport(event);
    for (Listener<Teleport> listener : teleports) {
      listener.on(teleport);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(PlayerPortalEvent event) {
    if (portals.isEmpty() || event.isCancelled() || event.getTo() == null) {
      return;
    }
    dispatch(new BukkitPortal(event, event.getPlayer(), event.getTo().clone(), true));
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(EntityPortalEvent event) {
    if (portals.isEmpty() || event.isCancelled() || event.getEntity() instanceof Player || event.getTo() == null) {
      return;
    }
    dispatch(new BukkitPortal(event, event.getEntity(), event.getTo().clone(), false));
  }

  @EventHandler
  public void on(PlayerJoinEvent event) {
    ReactPlayer player = new BukkitReactPlayer(event.getPlayer());
    for (Listener<ReactPlayer> listener : joins) {
      listener.on(player);
    }
  }

  @EventHandler
  public void on(PlayerQuitEvent event) {
    ReactPlayer player = new BukkitReactPlayer(event.getPlayer());
    for (Listener<ReactPlayer> listener : quits) {
      listener.on(player);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(PlayerInteractEvent event) {
    if (interactions.isEmpty() || ProtectionProbe.isProbe(event)) {
      return;
    }
    dispatch(interactions, new BukkitCancellable(event.getPlayer(), event));
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(EntityDamageByEntityEvent event) {
    if (attacks.isEmpty()) {
      return;
    }
    Player player = playerDamager(event.getDamager());
    if (player != null) {
      dispatch(attacks, new BukkitCancellable(player, event));
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(PlayerItemConsumeEvent event) {
    if (!consumes.isEmpty()) {
      dispatch(consumes, new BukkitCancellable(event.getPlayer(), event));
    }
  }

  private void dispatch(BukkitPortal portal) {
    for (Listener<Portal> listener : portals) {
      listener.on(portal);
    }
  }

  private static void dispatch(List<Listener<PlayerHost.Cancellable>> listeners, BukkitCancellable event) {
    for (Listener<PlayerHost.Cancellable> listener : listeners) {
      listener.on(event);
    }
  }

  private static Player playerDamager(Entity damager) {
    if (damager instanceof Player player) {
      return player;
    }
    if (damager instanceof Projectile projectile) {
      ProjectileSource shooter = projectile.getShooter();
      if (shooter instanceof Player player) {
        return player;
      }
    }
    return null;
  }

  private synchronized <T> Subscription subscribe(List<Listener<T>> listeners, Listener<T> listener) {
    listeners.add(listener);
    subscribers++;
    if (!registered && React.instance != null) {
      React.instance.registerListener(this);
      registered = true;
    }
    return () -> unsubscribe(listeners, listener);
  }

  private synchronized <T> void unsubscribe(List<Listener<T>> listeners, Listener<T> listener) {
    if (!listeners.remove(listener)) {
      return;
    }
    subscribers--;
    if (subscribers <= 0 && registered) {
      registered = false;
      if (React.instance != null) {
        React.instance.unregisterListener(this);
      }
    }
  }

  private static Method method(String name, Class<?>... parameters) {
    try {
      return Player.class.getMethod(name, parameters);
    } catch (NoSuchMethodException unavailable) {
      return null;
    }
  }

  private static Object invoke(Method method, ReactPlayer player, Object... arguments) {
    try {
      return method.invoke(player.handle(), arguments);
    } catch (IllegalAccessException failure) {
      throw new IllegalStateException(failure);
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof RuntimeException runtime) {
        throw runtime;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new IllegalStateException(cause);
    }
  }

  private static final class BukkitMove implements Move {
    private final PlayerMoveEvent event;
    private final ReactPlayer player;

    private BukkitMove(PlayerMoveEvent event) {
      this.event = event;
      this.player = new BukkitReactPlayer(event.getPlayer());
    }

    @Override
    public ReactPlayer player() {
      return player;
    }

    @Override
    public GameLocation from() {
      return BukkitGameEntity.location(event.getFrom());
    }

    @Override
    public GameLocation to() {
      return BukkitGameEntity.location(event.getTo());
    }

    @Override
    public boolean isCancelled() {
      return event.isCancelled();
    }

    @Override
    public void cancel() {
      event.setTo(event.getFrom());
    }
  }

  private static final class BukkitTeleport implements Teleport {
    private final PlayerTeleportEvent event;
    private final ReactPlayer player;

    private BukkitTeleport(PlayerTeleportEvent event) {
      this.event = event;
      this.player = new BukkitReactPlayer(event.getPlayer());
    }

    @Override
    public ReactPlayer player() {
      return player;
    }

    @Override
    public GameLocation to() {
      return BukkitGameEntity.location(event.getTo());
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

  private static final class BukkitCancellable implements PlayerHost.Cancellable {
    private final ReactPlayer player;
    private final org.bukkit.event.Cancellable event;

    private BukkitCancellable(Player player, org.bukkit.event.Cancellable event) {
      this.player = new BukkitReactPlayer(player);
      this.event = event;
    }

    @Override
    public ReactPlayer player() {
      return player;
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

  private static final class BukkitPortal implements Portal {
    private final org.bukkit.event.Cancellable event;
    private final Entity entity;
    private final Location destination;
    private final boolean player;

    private BukkitPortal(org.bukkit.event.Cancellable event, Entity entity, Location destination, boolean player) {
      this.event = event;
      this.entity = entity;
      this.destination = destination;
      this.player = player;
    }

    @Override
    public UUID entityId() {
      return entity.getUniqueId();
    }

    @Override
    public boolean isPlayer() {
      return player;
    }

    @Override
    public Object handle() {
      return entity;
    }

    @Override
    public GameLocation destination() {
      return BukkitGameEntity.location(destination);
    }

    @Override
    public boolean isCancelled() {
      return event.isCancelled();
    }

    @Override
    public void cancel() {
      event.setCancelled(true);
    }

    @Override
    public boolean canTravel() {
      if (entity instanceof Player traveller) {
        return traveller.isOnline() && !traveller.isDead();
      }
      return entity.isValid() && !entity.isDead();
    }

    @Override
    public CompletableFuture<Boolean> travel() {
      return EntityTeleports.teleport(React.instance, entity, destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }
  }
}
