package art.arcane.react.core.gui;

import art.arcane.react.React;
import art.arcane.react.platform.SettingsClick;
import art.arcane.react.platform.SettingsScreen;
import art.arcane.react.platform.SettingsScreenHost;
import art.arcane.react.platform.SettingsTile;
import art.arcane.react.util.inventorygui.UIStaticDecorator;
import art.arcane.volmlib.util.data.MaterialBlock;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.inventorygui.WindowResolution;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class BukkitSettingsScreenHost implements SettingsScreenHost {
  static final BukkitSettingsScreenHost INSTANCE = new BukkitSettingsScreenHost();
  private final Map<UUID, UIWindow> windows = new ConcurrentHashMap<>();

  private BukkitSettingsScreenHost() {
  }

  @Override
  public void open(UUID viewerId, SettingsScreen screen, ClickHandler handler) {
    Player player = viewerId == null ? null : Bukkit.getPlayer(viewerId);
    if (player == null || screen == null) {
      return;
    }

    UIWindow window = windows.computeIfAbsent(viewerId, (id) -> new UIWindow(React.instance, player));
    window.onClosed((w) -> windows.remove(viewerId, window));
    if (window.getResolution() != WindowResolution.W9_H6) {
      window.setResolution(WindowResolution.W9_H6);
    }
    if (window.getViewportHeight() != screen.rows()) {
      window.setViewportHeight(screen.rows());
    }
    if (!Objects.equals(window.getTitle(), screen.title())) {
      window.setTitle(screen.title());
    }
    window.setDecorator(new UIStaticDecorator(new UIElement("bg").setMaterial(new MaterialBlock(material(screen.background(), "black_stained_glass_pane")))));
    window.clearElements();

    for (SettingsTile tile : screen.tiles()) {
      UIElement element = new UIElement(tile.key())
          .setMaterial(new MaterialBlock(material(tile.icon(), tile.fallbackIcon())))
          .setName(tile.name());
      for (String line : tile.lore()) {
        element.addLore(line);
      }
      if (tile.enchanted()) {
        element.setEnchanted(true);
      }
      if (handler != null && tile.actionFor(SettingsClick.LEFT) != null) {
        element.onLeftClick((e) -> handler.onClick(viewerId, tile, SettingsClick.LEFT, tile.actionFor(SettingsClick.LEFT)));
      }
      if (handler != null && tile.actionFor(SettingsClick.RIGHT) != null) {
        element.onRightClick((e) -> handler.onClick(viewerId, tile, SettingsClick.RIGHT, tile.actionFor(SettingsClick.RIGHT)));
      }
      window.setElement(tile.position(), tile.row(), element);
    }

    window.open();
    windows.put(viewerId, window);
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

  private static Material material(String icon, String fallback) {
    Material material = icon == null ? null : Material.matchMaterial(icon);
    if (material == null && fallback != null) {
      material = Material.matchMaterial(fallback);
    }
    return material == null ? Material.CHEST : material;
  }
}
