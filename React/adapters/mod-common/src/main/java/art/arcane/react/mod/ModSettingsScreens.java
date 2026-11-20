package art.arcane.react.mod;

import art.arcane.react.api.action.Action;
import art.arcane.react.api.feature.Feature;
import art.arcane.react.api.tweak.Tweak;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.core.controller.FeatureController;
import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.core.controller.TweakController;
import art.arcane.react.core.gui.ReactGuiTaxonomy;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.GuiMessages;
import art.arcane.react.util.format.C;
import art.arcane.react.util.project.config.ConfigLocalization;
import art.arcane.react.util.project.registry.Registered;
import art.arcane.volmlib.util.localization.MessageArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The feature, tweak and action browser on mods: a root page with the three sections, then paged lists where a right
 * click toggles a component and saves its config, as in the Bukkit config editor.
 */
final class ModSettingsScreens {
    private static final int PAGE_SIZE = 45;
    private static final int BACK_SLOT = 45;
    private static final int PREVIOUS_SLOT = 48;
    private static final int PAGE_SLOT = 49;
    private static final int NEXT_SLOT = 50;

    private enum Section {
        FEATURES("feature", GuiMessages.CONFIG_FEATURES),
        TWEAKS("tweak", GuiMessages.CONFIG_TWEAKS),
        ACTIONS("action", GuiMessages.CONFIG_ACTIONS);

        private final String category;
        private final art.arcane.volmlib.util.localization.TextKey label;

        Section(String category, art.arcane.volmlib.util.localization.TextKey label) {
            this.category = category;
            this.label = label;
        }
    }

    private record Entry(Registered component, boolean enabled, boolean running) {
    }

    private ModSettingsScreens() {
    }

    static void openRoot(ServerPlayer player) {
        FeatureController features = ReactComponents.features();
        TweakController tweaks = ReactComponents.tweaks();
        ActionController actions = ReactComponents.actions();
        List<ItemStack> items = background();
        Map<Integer, ModSettingsMenu.Button> buttons = new HashMap<>();
        int featureTotal = features == null || features.getFeatures() == null ? 0 : features.getFeatures().size();
        int featureActive = features == null || features.getActiveFeatures() == null ? 0 : features.getActiveFeatures().size();
        int tweakTotal = tweaks == null || tweaks.getTweaks() == null ? 0 : tweaks.getTweaks().size();
        int tweakActive = tweaks == null || tweaks.getActiveTweaks() == null ? 0 : tweaks.getActiveTweaks().size();
        int actionTotal = actions == null || actions.getActions() == null ? 0 : actions.getActions().size();
        place(items, buttons, 20, stack(Items.COMPARATOR, C.AQUA + ReactLanguage.plain(GuiMessages.CONFIG_FEATURE_COUNT,
                MessageArgument.untrusted("active", featureActive), MessageArgument.untrusted("total", featureTotal)), List.of()),
                (ModSettingsMenu.Click click) -> openList(player, Section.FEATURES, 0));
        place(items, buttons, 22, stack(Items.REDSTONE_TORCH, C.AQUA + ReactLanguage.plain(GuiMessages.CONFIG_TWEAK_COUNT,
                MessageArgument.untrusted("active", tweakActive), MessageArgument.untrusted("total", tweakTotal)), List.of()),
                (ModSettingsMenu.Click click) -> openList(player, Section.TWEAKS, 0));
        place(items, buttons, 24, stack(Items.LEVER, C.AQUA + ReactLanguage.plain(GuiMessages.CONFIG_ACTION_COUNT,
                MessageArgument.untrusted("count", actionTotal)), List.of()),
                (ModSettingsMenu.Click click) -> openList(player, Section.ACTIONS, 0));
        open(player, ReactLanguage.plain(GuiMessages.CONFIG_TITLE), items, buttons);
    }

    private static void openList(ServerPlayer player, Section section, int requestedPage) {
        List<Entry> entries = entries(section);
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        List<ItemStack> items = background();
        Map<Integer, ModSettingsMenu.Button> buttons = new HashMap<>();
        int from = page * PAGE_SIZE;
        int to = Math.min(entries.size(), from + PAGE_SIZE);
        for (int index = from; index < to; index++) {
            Entry entry = entries.get(index);
            place(items, buttons, index - from, entryStack(section, entry), (ModSettingsMenu.Click click) -> {
                if (click == ModSettingsMenu.Click.RIGHT) {
                    toggle(section, entry);
                    openList(player, section, page);
                }
            });
        }
        place(items, buttons, BACK_SLOT, stack(Items.ARROW, C.WHITE + ReactLanguage.plain(GuiMessages.BACK), List.of()),
                (ModSettingsMenu.Click click) -> openRoot(player));
        if (page > 0) {
            place(items, buttons, PREVIOUS_SLOT, stack(Items.ARROW, C.WHITE + ReactLanguage.plain(GuiMessages.PREVIOUS), List.of()),
                    (ModSettingsMenu.Click click) -> openList(player, section, page - 1));
        }
        items.set(PAGE_SLOT, stack(Items.PAPER, C.GRAY + ReactLanguage.plain(GuiMessages.PAGE,
                MessageArgument.untrusted("current", page + 1), MessageArgument.untrusted("total", pages),
                MessageArgument.untrusted("from", entries.isEmpty() ? 0 : from + 1), MessageArgument.untrusted("to", to)), List.of()));
        if (page + 1 < pages) {
            place(items, buttons, NEXT_SLOT, stack(Items.ARROW, C.WHITE + ReactLanguage.plain(GuiMessages.NEXT), List.of()),
                    (ModSettingsMenu.Click click) -> openList(player, section, page + 1));
        }
        String title = ReactLanguage.plain(GuiMessages.CONFIG_SECTION_TITLE,
                MessageArgument.untrusted("section", ReactLanguage.plain(section.label)));
        open(player, title, items, buttons);
    }

    private static void toggle(Section section, Entry entry) {
        ReactComponents.host().ifPresent(host ->
                host.applyConfigValue(section.category + "." + entry.component().getId() + ".enabled", !entry.enabled()));
    }

    private static List<Entry> entries(Section section) {
        List<Entry> entries = new ArrayList<>();
        switch (section) {
            case FEATURES -> {
                FeatureController controller = ReactComponents.features();
                if (controller != null && controller.getFeatures() != null) {
                    collect(entries, controller.getFeatures().all(), controller.getActiveFeatures().keySet(), Feature::isEnabled);
                }
            }
            case TWEAKS -> {
                TweakController controller = ReactComponents.tweaks();
                if (controller != null && controller.getTweaks() != null) {
                    collect(entries, controller.getTweaks().all(), controller.getActiveTweaks().keySet(), Tweak::isEnabled);
                }
            }
            case ACTIONS -> {
                ActionController controller = ReactComponents.actions();
                if (controller != null && controller.getActions() != null) {
                    for (Action<?> action : controller.getActions().all()) {
                        entries.add(new Entry(action, action.isEnabled(), action.isEnabled()));
                    }
                }
            }
        }
        entries.sort(Comparator.comparing((Entry entry) -> entry.component().getId()));
        return entries;
    }

    private static <T extends Registered> void collect(List<Entry> entries, Collection<T> components, Set<String> active,
                                                       Predicate<T> enabled) {
        for (T component : components) {
            entries.add(new Entry(component, enabled.test(component), active.contains(component.getId())));
        }
    }

    private static ItemStack entryStack(Section section, Entry entry) {
        Registered component = entry.component();
        List<String> lore = new ArrayList<>();
        String status = ReactLanguage.plain(entry.running() ? GuiMessages.CONFIG_STATUS_ACTIVE : GuiMessages.CONFIG_STATUS_INACTIVE);
        String toggle = ReactLanguage.plain(entry.enabled() ? GuiMessages.CONFIG_STATUS_ON : GuiMessages.CONFIG_STATUS_OFF);
        lore.add((entry.enabled() ? C.GREEN : C.RED) + toggle + C.GRAY + " | " + status);
        lore.add(C.DARK_GRAY + ReactLanguage.plain(GuiMessages.CONFIG_PATH,
                MessageArgument.untrusted("path", section.category + "." + component.getId())));
        String description = ConfigLocalization.description(component.getClass());
        if (!description.isBlank()) {
            lore.add(C.GRAY + description);
        }
        lore.add(C.YELLOW + ReactLanguage.plain(GuiMessages.CONFIG_TOGGLE_ENABLED));
        return stack(icon(ReactGuiTaxonomy.iconForId(component.getId())), (entry.enabled() ? C.GREEN : C.RED) + component.getName(), lore);
    }

    private static Item icon(String key) {
        Item item = key == null ? Items.AIR : VanillaApi.itemOrAir(key);
        return item == Items.AIR ? Items.CHEST : item;
    }

    private static List<ItemStack> background() {
        Item pane = VanillaApi.vanillaItem("black_stained_glass_pane");
        List<ItemStack> items = new ArrayList<>(ModSettingsMenu.ROWS * 9);
        for (int slot = 0; slot < ModSettingsMenu.ROWS * 9; slot++) {
            ItemStack stack = new ItemStack(pane);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
            items.add(stack);
        }
        return items;
    }

    private static void place(List<ItemStack> items, Map<Integer, ModSettingsMenu.Button> buttons, int slot, ItemStack stack,
                              ModSettingsMenu.Button button) {
        items.set(slot, stack);
        buttons.put(slot, button);
    }

    private static ItemStack stack(Item item, String name, List<String> lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, ModLegacyText.component(name));
        if (!lore.isEmpty()) {
            List<Component> lines = new ArrayList<>(lore.size());
            for (String line : lore) {
                lines.add(ModLegacyText.component(line));
            }
            stack.set(DataComponents.LORE, new ItemLore(lines));
        }
        return stack;
    }

    private static void open(ServerPlayer player, String title, List<ItemStack> items, Map<Integer, ModSettingsMenu.Button> buttons) {
        if (player.containerMenu instanceof ModSettingsMenu current && current.reusableFor(title)) {
            current.show(items, buttons);
            return;
        }
        player.openMenu(new SimpleMenuProvider((int id, Inventory inventory, Player opener) -> {
            ModSettingsMenu menu = new ModSettingsMenu(id, inventory, player, title);
            menu.show(items, buttons);
            return menu;
        }, ModLegacyText.component(title)));
    }
}
