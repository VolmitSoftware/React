package art.arcane.react.api.test.checks;

import art.arcane.react.React;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.rendering.RendererUnknown;
import art.arcane.react.api.test.ReactAsyncSubsystemCheck;
import art.arcane.react.api.test.TestReport;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.TestMessages;
import art.arcane.react.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MapsFrameWallCheck implements ReactAsyncSubsystemCheck {
  private static final String SUBSYSTEM = "maps";
  private static final long PHASE_TIMEOUT_MS = 15_000L;
  private static final int POLL_TICKS = 5;
  private static final int PHASE_PLANE_STRIDE = 3;
  private static final int GROUND_CLEARANCE = 8;
  private static final int CLEAR_SEARCH_HEIGHT = 24;
  private static final List<Phase> PHASES = List.of(
      new Phase("frame-wall", false, false),
      new Phase("glow-frame-wall", true, true),
      new Phase("mixed-frame-wall", false, true)
  );

  @Override
  public String subsystem() {
    return SUBSYSTEM;
  }

  @Override
  public void run(TestReport report, Runnable onDone) {
    AtomicBoolean completed = new AtomicBoolean(false);
    Runnable done = () -> {
      if (completed.compareAndSet(false, true)) {
        onDone.run();
      }
    };

    MapController controller = React.controller(MapController.class);
    if (controller == null) {
      skipAll(report, ReactLanguage.raw(TestMessages.MAP_CONTROLLER_UNAVAILABLE));
      done.run();
      return;
    }

    if (!controller.isMegamapEnabled()) {
      skipAll(
          report,
          ReactLanguage.raw("test.map.frame_wall_disabled", "Megamap tiling is disabled; adjacent frames never form a wall.")
      );
      done.run();
      return;
    }

    if (Bukkit.getWorlds().isEmpty()) {
      skipAll(
          report,
          ReactLanguage.raw("test.map.frame_wall_no_worlds", "No worlds are loaded; cannot build a live item-frame wall.")
      );
      done.run();
      return;
    }

    ReactRenderer renderer = controller.getRendererById(RendererUnknown.ID);
    if (renderer == null) {
      skipAll(report, ReactLanguage.raw(TestMessages.MAP_REGISTRY_EMPTY));
      done.run();
      return;
    }

    World world = Bukkit.getWorlds().get(0);
    runPhase(report, controller, renderer, world, world.getSpawnLocation(), 0, done);
  }

  private void skipAll(TestReport report, String detail) {
    for (Phase phase : PHASES) {
      report.skip(SUBSYSTEM, phase.name(), detail);
    }
  }

  private void runPhase(
      TestReport report,
      MapController controller,
      ReactRenderer renderer,
      World world,
      Location origin,
      int index,
      Runnable onDone
  ) {
    if (index >= PHASES.size()) {
      onDone.run();
      return;
    }

    Phase phase = PHASES.get(index);
    AtomicBoolean advanced = new AtomicBoolean(false);
    Runnable advance = () -> {
      if (advanced.compareAndSet(false, true)) {
        runPhase(report, controller, renderer, world, origin, index + 1, onDone);
      }
    };

    J.s(origin, () -> {
      try {
        buildWall(report, controller, renderer, world, origin, phase, index, advance);
      } catch (Throwable e) {
        report.warn(
            SUBSYSTEM,
            phase.name(),
            ReactLanguage.raw("test.map.frame_wall_setup_failed", "Could not build a live item-frame wall:")
                + " " + describe(e)
        );
        advance.run();
      }
    });
  }

  private void buildWall(
      TestReport report,
      MapController controller,
      ReactRenderer renderer,
      World world,
      Location origin,
      Phase phase,
      int index,
      Runnable advance
  ) {
    int blockX = alignBlockX(origin.getBlockX());
    int supportZ = alignBlockZ(origin.getBlockZ()) + (index * PHASE_PLANE_STRIDE);
    int frameZ = supportZ + 1;
    int blockY = findClearY(world, blockX, supportZ, frameZ);
    if (blockY == Integer.MIN_VALUE) {
      report.skip(
          SUBSYSTEM,
          phase.name(),
          ReactLanguage.raw("test.map.frame_wall_no_space", "No clear air column near spawn to build a two-frame map wall.")
      );
      advance.run();
      return;
    }

    Wall wall = new Wall(world, blockX, blockY, supportZ, frameZ);
    try {
      wall.placeSupports();
      wall.raiseWall(phase, controller.createMap(world, renderer));
    } catch (Throwable e) {
      wall.dismantle();
      report.warn(
          SUBSYSTEM,
          phase.name(),
          ReactLanguage.raw("test.map.frame_wall_setup_failed", "Could not build a live item-frame wall:")
              + " " + describe(e)
      );
      advance.run();
      return;
    }

    React.verbose("frame-wall " + phase.name() + " built at " + blockX + "," + blockY + "," + frameZ
        + " seed map " + wall.seedMapId());
    pollWall(report, controller, wall, phase, System.currentTimeMillis() + PHASE_TIMEOUT_MS, advance);
  }

  private void pollWall(
      TestReport report,
      MapController controller,
      Wall wall,
      Phase phase,
      long deadlineMs,
      Runnable advance
  ) {
    J.s(wall.anchor(), () -> {
      Observation observation;
      try {
        // The frame-repair sweep walks every loaded chunk in small batches, so schedule
        // direct refreshes of this wall's frames instead of waiting for the cursor.
        wall.nudgeController(controller);
        observation = wall.observe(controller);
      } catch (Throwable e) {
        wall.dismantle();
        report.fail(
            SUBSYSTEM,
            phase.name(),
            ReactLanguage.raw("test.map.frame_wall_probe_failed", "Probing the live item-frame wall failed:")
                + " " + describe(e)
        );
        advance.run();
        return;
      }

      if (observation.settled()) {
        wall.dismantle();
        report.pass(
            SUBSYSTEM,
            phase.name(),
            ReactLanguage.raw("test.map.frame_wall_pass", "Cloned React maps in adjacent frames split to distinct ids and solved into a two-tile wall.")
                + " " + observation.summary()
        );
        advance.run();
        return;
      }

      if (System.currentTimeMillis() >= deadlineMs) {
        wall.dismantle();
        report.fail(
            SUBSYSTEM,
            phase.name(),
            ReactLanguage.raw("test.map.frame_wall_fail", "Adjacent item frames never settled into a two-tile megamap wall:")
                + " " + observation.summary()
        );
        advance.run();
        return;
      }

      pollWall(report, controller, wall, phase, deadlineMs, advance);
    }, POLL_TICKS);
  }

  private int findClearY(World world, int blockX, int supportZ, int frameZ) {
    int highest = world.getHighestBlockYAt(blockX, supportZ);
    highest = Math.max(highest, world.getHighestBlockYAt(blockX + 1, supportZ));
    highest = Math.max(highest, world.getHighestBlockYAt(blockX, frameZ));
    highest = Math.max(highest, world.getHighestBlockYAt(blockX + 1, frameZ));

    int start = Math.max(world.getMinHeight() + 1, highest + GROUND_CLEARANCE);
    int limit = Math.min(world.getMaxHeight() - 2, start + CLEAR_SEARCH_HEIGHT);
    for (int y = start; y <= limit; y++) {
      if (isClear(world, blockX, y, supportZ, frameZ)) {
        return y;
      }
    }

    return Integer.MIN_VALUE;
  }

  private boolean isClear(World world, int blockX, int y, int supportZ, int frameZ) {
    return world.getBlockAt(blockX, y, supportZ).getType() == Material.AIR
        && world.getBlockAt(blockX + 1, y, supportZ).getType() == Material.AIR
        && world.getBlockAt(blockX, y, frameZ).getType() == Material.AIR
        && world.getBlockAt(blockX + 1, y, frameZ).getType() == Material.AIR;
  }

  // Keep the whole fixture inside one chunk so both frames share a region owner on Folia.
  private int alignBlockX(int blockX) {
    return (blockX & 15) == 15 ? blockX - 1 : blockX;
  }

  private int alignBlockZ(int blockZ) {
    int local = blockZ & 15;
    return local > 8 ? blockZ - (local - 8) : blockZ;
  }

  private static String describe(Throwable e) {
    return e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " - " + e.getMessage());
  }

  private record Phase(String name, boolean firstGlow, boolean secondGlow) {
  }

  private record Observation(
      boolean framesPresent,
      boolean tracked,
      boolean distinctMapIds,
      boolean walled,
      int firstMapId,
      int secondMapId,
      MegamapGrid.MegamapTile firstTile,
      MegamapGrid.MegamapTile secondTile,
      boolean duplicateReported
  ) {
    private static Observation missing(boolean duplicateReported) {
      return new Observation(false, false, false, false, -1, -1, null, null, duplicateReported);
    }

    private boolean settled() {
      return framesPresent && tracked && distinctMapIds && walled;
    }

    private String summary() {
      return "frames=" + framesPresent
          + " tracked=" + tracked
          + " ids=" + firstMapId + "/" + secondMapId
          + " tiles=" + tileText(firstTile) + " " + tileText(secondTile)
          + " duplicate-seen=" + duplicateReported;
    }

    private static String tileText(MegamapGrid.MegamapTile tile) {
      if (tile == null) {
        return "none";
      }

      return tile.gridWidth() + "x" + tile.gridHeight() + "@" + tile.tileX() + "," + tile.tileY();
    }
  }

  private static final class Wall {
    private final World world;
    private final int blockX;
    private final int blockY;
    private final int supportZ;
    private final int frameZ;
    private final Location anchor;
    private final List<UUID> frameIds;
    private final Map<Integer, MapView> mintedViews;
    private boolean supportsPlaced;
    private boolean dismantled;
    private boolean duplicateReported;
    private int seedMapId;

    private Wall(World world, int blockX, int blockY, int supportZ, int frameZ) {
      this.world = world;
      this.blockX = blockX;
      this.blockY = blockY;
      this.supportZ = supportZ;
      this.frameZ = frameZ;
      this.anchor = new Location(world, blockX + 1.0D, blockY + 0.5D, frameZ + 0.5D);
      this.frameIds = new ArrayList<UUID>(2);
      this.mintedViews = new LinkedHashMap<Integer, MapView>();
      this.seedMapId = -1;
    }

    private Location anchor() {
      return anchor;
    }

    private int seedMapId() {
      return seedMapId;
    }

    private void placeSupports() {
      // With no players online the fixture chunk unloads out from under the frames;
      // pin it for the phase and release the ticket in dismantle().
      world.addPluginChunkTicket(blockX >> 4, frameZ >> 4, React.instance);
      world.getBlockAt(blockX, blockY, supportZ).setType(Material.BARRIER, false);
      world.getBlockAt(blockX + 1, blockY, supportZ).setType(Material.BARRIER, false);
      supportsPlaced = true;
    }

    // Both frames intentionally receive clones of the same stack, so the wall starts life
    // holding one duplicated map id and has to be split before it can tile.
    private void raiseWall(Phase phase, ItemStack map) {
      MapView view = viewOf(map);
      if (view == null) {
        throw new IllegalStateException("react map item carries no map view");
      }

      seedMapId = view.getId();
      remember(view);

      ItemFrame first = spawnFrame(phase.firstGlow(), blockX);
      frameIds.add(first.getUniqueId());
      ItemFrame second = spawnFrame(phase.secondGlow(), blockX + 1);
      frameIds.add(second.getUniqueId());
      first.setItem(map.clone());
      second.setItem(map.clone());
    }

    private ItemFrame spawnFrame(boolean glow, int x) {
      Location at = new Location(world, x + 0.5D, blockY + 0.5D, frameZ + 0.5D);
      ItemFrame frame;
      if (glow) {
        frame = world.spawn(at, GlowItemFrame.class);
      } else {
        frame = world.spawn(at, ItemFrame.class);
      }

      if (frame.getFacing() != BlockFace.SOUTH) {
        frame.setFacingDirection(BlockFace.SOUTH, true);
      }

      frame.setRotation(Rotation.NONE);
      frame.setVisible(true);
      frame.setFixed(false);
      frame.setPersistent(false);
      return frame;
    }

    private void nudgeController(MapController controller) {
      try {
        for (int i = 0; i < frameIds.size(); i++) {
          ItemFrame frame = frameAt(i);
          if (frame != null) {
            controller.scheduleFrameRefresh(frame);
          }
        }
      } catch (Throwable e) {
        React.verbose("frame-wall refresh nudge unavailable: " + describe(e));
      }
    }

    private Observation observe(MapController controller) {
      ItemFrame first = frameAt(0);
      ItemFrame second = frameAt(1);
      if (first == null || second == null) {
        return Observation.missing(duplicateReported);
      }

      MapView firstView = viewOf(first.getItem());
      MapView secondView = viewOf(second.getItem());
      if (firstView == null || secondView == null) {
        return Observation.missing(duplicateReported);
      }

      remember(firstView);
      remember(secondView);
      int firstMapId = firstView.getId();
      int secondMapId = secondView.getId();
      boolean distinctMapIds = firstMapId != secondMapId;
      if (!distinctMapIds) {
        MegamapGrid.MegamapDefect defect = controller.megamapDefectFor(firstView);
        if (defect != null && defect.reason() == MegamapGrid.DefectReason.DUPLICATE_MAP_ID) {
          duplicateReported = true;
        }
      }

      boolean tracked = controller.hasFrameAnchor(firstView) && controller.hasFrameAnchor(secondView);
      MegamapGrid.MegamapTile firstTile = controller.megamapTileFor(firstView);
      MegamapGrid.MegamapTile secondTile = controller.megamapTileFor(secondView);
      return new Observation(
          true,
          tracked,
          distinctMapIds,
          distinctMapIds && isPairedWall(firstTile, secondTile),
          firstMapId,
          secondMapId,
          firstTile,
          secondTile,
          duplicateReported
      );
    }

    private boolean isPairedWall(MegamapGrid.MegamapTile first, MegamapGrid.MegamapTile second) {
      if (first == null || second == null) {
        return false;
      }

      if (first.gridWidth() != second.gridWidth() || first.gridHeight() != second.gridHeight()) {
        return false;
      }

      if (first.tiles() != 2) {
        return false;
      }

      return first.tileX() != second.tileX() || first.tileY() != second.tileY();
    }

    private ItemFrame frameAt(int index) {
      if (index >= frameIds.size()) {
        return null;
      }

      Entity entity = Bukkit.getEntity(frameIds.get(index));
      if (!(entity instanceof ItemFrame frame) || !frame.isValid()) {
        return null;
      }

      return frame;
    }

    private void remember(MapView view) {
      if (view != null) {
        mintedViews.put(view.getId(), view);
      }
    }

    private MapView viewOf(ItemStack item) {
      if (item == null || item.getType() != Material.FILLED_MAP) {
        return null;
      }

      if (!(item.getItemMeta() instanceof MapMeta meta)) {
        return null;
      }

      return meta.getMapView();
    }

    private void dismantle() {
      if (dismantled) {
        return;
      }
      dismantled = true;

      for (UUID frameId : frameIds) {
        try {
          Entity entity = Bukkit.getEntity(frameId);
          if (entity != null) {
            entity.remove();
          }
        } catch (Throwable e) {
          React.verbose("frame-wall teardown kept frame " + frameId + ": " + describe(e));
        }
      }
      frameIds.clear();

      for (MapView view : mintedViews.values()) {
        try {
          for (MapRenderer mapRenderer : new ArrayList<MapRenderer>(view.getRenderers())) {
            view.removeRenderer(mapRenderer);
          }
        } catch (Throwable e) {
          React.verbose("frame-wall teardown kept map " + view.getId() + ": " + describe(e));
        }
      }
      React.verbose("frame-wall discarded maps " + mintedViews.keySet());
      mintedViews.clear();

      if (supportsPlaced) {
        clearSupport(blockX);
        clearSupport(blockX + 1);
        supportsPlaced = false;
      }

      try {
        world.removePluginChunkTicket(blockX >> 4, frameZ >> 4, React.instance);
      } catch (Throwable e) {
        React.verbose("frame-wall teardown kept chunk ticket: " + describe(e));
      }
    }

    private void clearSupport(int x) {
      try {
        Block block = world.getBlockAt(x, blockY, supportZ);
        if (block.getType() == Material.BARRIER) {
          block.setType(Material.AIR, false);
        }
      } catch (Throwable e) {
        React.verbose("frame-wall teardown kept support " + x + "," + blockY + "," + supportZ + ": " + describe(e));
      }
    }
  }
}
