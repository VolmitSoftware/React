package art.arcane.react.core.gui;

import art.arcane.react.React;
import art.arcane.react.platform.SettingsClick;
import art.arcane.react.platform.SettingsScreen;
import art.arcane.react.platform.SettingsScreenHost;
import art.arcane.react.platform.SettingsTile;
import art.arcane.react.util.inventorygui.CustomUIElement;
import art.arcane.react.util.inventorygui.UIStaticDecorator;
import art.arcane.volmlib.util.data.MaterialBlock;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.inventorygui.WindowResolution;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

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
    window.onClosed((w) -> {
      windows.remove(viewerId, window);
      if (handler != null) {
        handler.onClosed(viewerId);
      }
    });
    WindowResolution resolution = screen.dropper() ? WindowResolution.W3_H3 : WindowResolution.W9_H6;
    if (window.getResolution() != resolution) {
      window.setResolution(resolution);
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
      Material material = material(tile.icon(), tile.fallbackIcon());
      UIElement element = tile.dyeColor() == null
          ? new UIElement(tile.key()).setMaterial(new MaterialBlock(material))
          : new CustomUIElement(tile.key(), dyed(material, tile.dyeColor()));
      element.setName(tile.name());
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
      if (handler != null && tile.actionFor(SettingsClick.SHIFT_LEFT) != null) {
        element.onShiftLeftClick((e) -> handler.onClick(viewerId, tile, SettingsClick.SHIFT_LEFT, tile.actionFor(SettingsClick.SHIFT_LEFT)));
      }
      if (handler != null && tile.actionFor(SettingsClick.SHIFT_RIGHT) != null) {
        element.onShiftRightClick((e) -> handler.onClick(viewerId, tile, SettingsClick.SHIFT_RIGHT, tile.actionFor(SettingsClick.SHIFT_RIGHT)));
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

  private static ItemStack dyed(Material material, int rgb) {
    ItemStack stack = new ItemStack(material);
    ItemMeta meta = stack.getItemMeta();
    if (meta instanceof LeatherArmorMeta leather) {
      leather.setColor(org.bukkit.Color.fromRGB(rgb & 0xFFFFFF));
      stack.setItemMeta(leather);
    }
    return stack;
  }

  private static Material material(String icon, String fallback) {
    Material material = icon == null ? null : Material.matchMaterial(icon);
    if (material == null && fallback != null) {
      material = Material.matchMaterial(fallback);
    }
    return material == null ? Material.CHEST : material;
  }
}
