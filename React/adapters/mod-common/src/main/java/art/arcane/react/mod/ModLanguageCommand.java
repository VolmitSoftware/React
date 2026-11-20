package art.arcane.react.mod;

import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.platform.ReactCommandSender;
import art.arcane.volmlib.util.localization.BukkitLanguageMessages;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.ResolvedText;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.plugin.ComponentText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code /react language} on a mod server: the selection commands of the Bukkit language switcher, {@code self} and
 * {@code server} with a locale or {@code reset}, the same permissions and the same messages, as plain chat lines.
 */
final class ModLanguageCommand {
    private static final Logger LOGGER = Logger.getLogger(ReactModRuntime.NAME);
    private static final String NAME = ReactModRuntime.NAME;
    private static final String COMMAND = "react";
    private final Consumer<Runnable> mainThread;

    ModLanguageCommand(Consumer<Runnable> mainThread) {
        this.mainThread = mainThread;
    }

    boolean execute(ReactCommandSender sender, String[] arguments) {
        PluginLanguageService languages = ReactLanguage.selections();
        if (arguments.length == 0) {
            showCurrent(sender, languages);
            message(sender, BukkitLanguageMessages.SELECTION_USAGE, MessageArgument.untrusted("command", COMMAND));
            return true;
        }
        String scope = arguments[0].toLowerCase(Locale.ROOT);
        if ((!scope.equals("self") && !scope.equals("server")) || arguments.length > 2) {
            message(sender, BukkitLanguageMessages.SELECTION_USAGE, MessageArgument.untrusted("command", COMMAND));
            return true;
        }
        if (!allowed(sender, scope)) {
            return true;
        }
        if (arguments.length == 1) {
            showCurrent(sender, languages);
            sender.sendMessage(String.join(", ", languages.availableLocales()));
            return true;
        }
        select(sender, languages, scope, arguments[1]);
        return true;
    }

    List<String> complete(ReactCommandSender sender, String[] arguments) {
        List<String> candidates = new ArrayList<>();
        if (arguments.length == 1) {
            if (canSelectSelf(sender)) {
                candidates.add("self");
            }
            if (canSelectServer(sender)) {
                candidates.add("server");
            }
        } else if (arguments.length == 2 && (arguments[0].equalsIgnoreCase("self") && canSelectSelf(sender)
                || arguments[0].equalsIgnoreCase("server") && canSelectServer(sender))) {
            candidates.addAll(ReactLanguage.selections().availableLocales());
            if (arguments[0].equalsIgnoreCase("self")) {
                candidates.add("reset");
            }
        }
        String typed = arguments.length == 0 ? "" : arguments[arguments.length - 1].toLowerCase(Locale.ROOT);
        candidates.removeIf((String candidate) -> !candidate.toLowerCase(Locale.ROOT).startsWith(typed));
        return candidates;
    }

    private void showCurrent(ReactCommandSender sender, PluginLanguageService languages) {
        Optional<UUID> playerId = sender.playerId();
        Optional<String> personal = playerId.flatMap(languages::playerLocale);
        if (personal.isPresent()) {
            message(sender, BukkitLanguageMessages.CURRENT_WITH_PERSONAL,
                    MessageArgument.untrusted("locale", languages.defaultLocale()),
                    MessageArgument.untrusted("personal", personal.get()));
        } else {
            message(sender, BukkitLanguageMessages.CURRENT, MessageArgument.untrusted("locale", languages.defaultLocale()));
        }
    }

    private boolean allowed(ReactCommandSender sender, String scope) {
        if (scope.equals("self")) {
            if (sender.playerId().isEmpty()) {
                message(sender, BukkitLanguageMessages.PERSONAL_PLAYER_ONLY);
                return false;
            }
            if (!sender.hasPermission("volmit.language.self")) {
                message(sender, BukkitLanguageMessages.PERSONAL_PERMISSION);
                return false;
            }
            if (!sender.hasPermission("react.language.self")) {
                message(sender, BukkitLanguageMessages.PLUGIN_PERSONAL_PERMISSION, MessageArgument.untrusted("plugin", NAME));
                return false;
            }
            return true;
        }
        if (!canSelectServer(sender)) {
            message(sender, BukkitLanguageMessages.PLUGIN_SERVER_PERMISSION, MessageArgument.untrusted("plugin", NAME));
            return false;
        }
        return true;
    }

    private static boolean canSelectSelf(ReactCommandSender sender) {
        return sender.playerId().isPresent() && sender.hasPermission("volmit.language.self") && sender.hasPermission("react.language.self");
    }

    private static boolean canSelectServer(ReactCommandSender sender) {
        return sender.hasPermission("volmit.language.admin") || sender.hasPermission("react.use");
    }

    private void select(ReactCommandSender sender, PluginLanguageService languages, String scope, String locale) {
        if (scope.equals("server") && locale.equalsIgnoreCase("reset")) {
            message(sender, BukkitLanguageMessages.SERVER_LOCALE_REQUIRED);
            return;
        }
        if (!locale.equalsIgnoreCase("reset") && languages.availableLocales().stream().noneMatch((String candidate) -> same(candidate, locale))) {
            message(sender, BukkitLanguageMessages.UNAVAILABLE_FOR_ALL, MessageArgument.untrusted("locale", locale));
            return;
        }
        message(sender, BukkitLanguageMessages.PREPARING, MessageArgument.untrusted("locale", locale), MessageArgument.untrusted("target", NAME));
        CompletableFuture<String> pending;
        if (scope.equals("self")) {
            UUID playerId = sender.playerId().orElseThrow();
            pending = locale.equalsIgnoreCase("reset")
                    ? languages.clearPlayer(playerId).thenApply((Void ignored) -> "reset")
                    : languages.selectPlayer(playerId, locale).thenApply((Void ignored) -> languages.effectiveLocale(playerId));
        } else {
            pending = languages.selectDefault(locale).thenApply((Void ignored) -> languages.defaultLocale());
        }
        pending.whenComplete((String applied, Throwable failure) -> {
            if (failure != null) {
                LOGGER.log(Level.SEVERE, "Unable to select " + locale + " for " + NAME, failure);
            }
            mainThread.accept(() -> feedback(sender, scope, locale, applied, failure));
        });
    }

    private void feedback(ReactCommandSender sender, String scope, String requested, String applied, Throwable failure) {
        if (failure != null) {
            message(sender, BukkitLanguageMessages.SAVE_FAILED, MessageArgument.untrusted("plugin", NAME));
        } else if (!same(requested, applied)) {
            message(sender, BukkitLanguageMessages.ENGLISH_FALLBACK, MessageArgument.untrusted("plugin", NAME),
                    MessageArgument.untrusted("locale", requested));
        } else if (scope.equals("self") && applied.equalsIgnoreCase("reset")) {
            message(sender, BukkitLanguageMessages.SERVER_DEFAULT_SELECTED, MessageArgument.untrusted("plugin", NAME));
        } else {
            message(sender, scope.equals("self") ? BukkitLanguageMessages.PERSONAL_SELECTED : BukkitLanguageMessages.SERVER_SELECTED,
                    MessageArgument.untrusted("plugin", NAME), MessageArgument.untrusted("locale", applied));
        }
    }

    private static boolean same(String first, String second) {
        return first != null && second != null && first.replace('-', '_').equalsIgnoreCase(second.replace('-', '_'));
    }

    private static void message(ReactCommandSender sender, TextKey key, MessageArgument... arguments) {
        MessageArgs.Builder builder = MessageArgs.builder();
        for (MessageArgument argument : arguments) {
            builder.untrusted(argument.name(), String.valueOf(argument.value()));
        }
        MessageArgs args = builder.build();
        String template;
        MessageArgs resolvedArguments;
        try {
            ResolvedText resolved = ReactLanguage.selections().snapshot(sender.playerId().orElse(null)).resolve(key, args);
            template = resolved.template();
            resolvedArguments = resolved.arguments();
        } catch (RuntimeException unresolved) {
            template = key.english();
            resolvedArguments = args;
        }
        for (MessageArgument argument : resolvedArguments.arguments().values()) {
            template = template.replace("{" + argument.name() + "}", String.valueOf(argument.value()));
        }
        sender.send(ComponentText.literal(template));
    }
}
