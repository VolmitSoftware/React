package art.arcane.react.core.gui;

import art.arcane.react.React;
import art.arcane.react.platform.MapHubAction;
import art.arcane.react.platform.MapHubClick;
import art.arcane.react.platform.MapHubIcon;
import art.arcane.react.platform.MapHubScreen;
import art.arcane.react.platform.MapHubScreenHost;
import art.arcane.react.platform.MapHubTile;
import art.arcane.react.util.inventorygui.UIStaticDecorator;
import art.arcane.volmlib.util.data.MaterialBlock;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.inventorygui.WindowResolution;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class BukkitMapHubScreenHost implements MapHubScreenHost {
  public static final BukkitMapHubScreenHost INSTANCE = new BukkitMapHubScreenHost();

  private BukkitMapHubScreenHost() {
  }

  @Override
  public void open(UUID viewerId, MapHubScreen screen, ClickHandler handler) {
    Player player = viewerId == null ? null : Bukkit.getPlayer(viewerId);
    if (player == null || screen == null) {
      return;
    }

    UIWindow window = new UIWindow(React.instance, player);
    window.setResolution(WindowResolution.W9_H6);
    window.setViewportHeight(screen.rows());
    window.setTitle(screen.title());
    window.setDecorator(new UIStaticDecorator(new UIElement("bg")
        .setMaterial(new MaterialBlock(materialFor(screen.background())))));

    for (MapHubTile tile : screen.tiles()) {
      UIElement element = new UIElement(tile.key())
          .setMaterial(new MaterialBlock(materialFor(tile.icon())))
          .setName(tile.name());
      for (String line : tile.lore()) {
        element.addLore(line);
      }
      bind(element, viewerId, tile, MapHubClick.LEFT, handler);
      bind(element, viewerId, tile, MapHubClick.RIGHT, handler);
      bind(element, viewerId, tile, MapHubClick.MIDDLE, handler);
      bind(element, viewerId, tile, MapHubClick.SHIFT_LEFT, handler);
      bind(element, viewerId, tile, MapHubClick.SHIFT_RIGHT, handler);
      window.setElement(tile.position(), tile.row(), element);
    }

    window.open();
  }

  @Override
  public void close(UUID viewerId) {
    Player player = viewerId == null ? null : Bukkit.getPlayer(viewerId);
    if (player != null) {
      player.closeInventory();
    }
  }

  @Override
  public void playPageTurn(UUID viewerId) {
    Player player = viewerId == null ? null : Bukkit.getPlayer(viewerId);
    if (player == null || !player.isOnline()) {
      return;
    }

    try {
      player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
      player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.455f);
      player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 1.855f);
    } catch (Throwable ignored) {
    }
  }

  private static void bind(UIElement element, UUID viewerId, MapHubTile tile, MapHubClick click, ClickHandler handler) {
    MapHubAction action = tile.actionFor(click);
    if (action == null || handler == null) {
      return;
    }

    switch (click) {
      case LEFT -> element.onLeftClick((e) -> handler.onClick(viewerId, tile, click, action));
      case RIGHT -> element.onRightClick((e) -> handler.onClick(viewerId, tile, click, action));
      case MIDDLE -> element.onMiddleClick((e) -> handler.onClick(viewerId, tile, click, action));
      case SHIFT_LEFT -> element.onShiftLeftClick((e) -> handler.onClick(viewerId, tile, click, action));
      case SHIFT_RIGHT -> element.onShiftRightClick((e) -> handler.onClick(viewerId, tile, click, action));
    }
  }

  static Material materialFor(MapHubIcon icon) {
    return switch (icon) {
      case BACKGROUND -> Material.BLACK_STAINED_GLASS_PANE;
      case ALL_MAPS, GROUP_MISC -> Material.FILLED_MAP;
      case MEGAMAP_SUMMARY -> Material.ITEM_FRAME;
      case BACK, PREVIOUS, NEXT -> Material.ARROW;
      case PAGE_INFO -> Material.PAPER;
      case GROUP_ADAPT -> Material.BOOKSHELF;
      case GROUP_IRIS -> Material.OAK_SAPLING;
      case GROUP_REACT -> Material.REDSTONE_TORCH;
      case GROUP_WORMHOLES -> Material.ENDER_PEARL;
      case GROUP_GLOSS -> Material.PAINTING;
      case GROUP_HIDDENORE -> Material.DIAMOND_ORE;
      case GROUP_BILETOOLS -> Material.LIME_DYE;
      case GROUP_ANIMUS -> Material.ARMOR_STAND;
      case GROUP_OWNERS -> Material.JUKEBOX;
      case GROUP_TICK -> Material.CLOCK;
      case GROUP_DIMENSIONS -> Material.RECOVERY_COMPASS;
      case GROUP_MEMORY -> Material.EXPERIENCE_BOTTLE;
      case GROUP_PLAYERS -> Material.PLAYER_HEAD;
      case GROUP_ENTITIES -> Material.ZOMBIE_HEAD;
      case GROUP_CHUNKS -> Material.MAP;
      case GROUP_REDSTONE -> Material.REDSTONE;
      case GROUP_HOPPERS -> Material.HOPPER;
      case GROUP_FLUIDS -> Material.WATER_BUCKET;
      case GROUP_PHYSICS -> Material.ANVIL;
      case GROUP_EVENTS -> Material.NOTE_BLOCK;
      case GROUP_SCHEDULER -> Material.COMPARATOR;
      case GROUP_CPU -> Material.BLAST_FURNACE;
      case GROUP_SAMPLER_TRENDS -> Material.COMPASS;
      case GROUP_WORLD_OVERLAYS -> Material.CARTOGRAPHY_TABLE;
      case GROUP_FALLBACK -> Material.BARRIER;
    };
  }
}
