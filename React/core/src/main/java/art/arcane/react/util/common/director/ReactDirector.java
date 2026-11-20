package art.arcane.react.util.director;

import art.arcane.react.content.directorcommand.CommandReact;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RuntimeMessages;
import art.arcane.react.platform.ReactCommandSender;
import art.arcane.react.platform.ReactPlatforms;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.director.DirectorEngineOptions;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.context.DirectorContextRegistry;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionMode;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionResult;
import art.arcane.volmlib.util.director.runtime.DirectorInvocation;
import art.arcane.volmlib.util.director.runtime.DirectorInvocationHook;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import art.arcane.volmlib.util.director.runtime.DirectorSender;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.plugin.ComponentText;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The {@code /react} command on any platform: permission gate, language shortcut, help pages, Director execution,
 * completion and the confirmation sounds. Platform modules hand it a {@link ReactCommandSender} and the raw words.
 */
public final class ReactDirector implements DirectorInvocationHook {
  public static final String ROOT_COMMAND = "react";
  public static final String ROOT_PERMISSION = "react.use";
  private static final MiniMessage HELP_MARKUP = MiniMessage.miniMessage();

  private volatile DirectorRuntimeEngine director;

  public DirectorRuntimeEngine getDirector() {
    DirectorRuntimeEngine engine = director;
    if (engine != null) {
      return engine;
    }
    synchronized (this) {
      if (director == null) {
        director = DirectorEngineFactory.create(
            new CommandReact(),
            DirectorEngineOptions.builder()
                .contexts(buildDirectorContexts())
                .dispatcher(ReactDirector::dispatchDirector)
                .invocationHook(this)
                .legacyHandlers(DirectorSystem.handlers)
                .textResolver(ReactLanguage.directorResolver())
                .build()
        );
      }
      return director;
    }
  }

  /**
   * Runs {@code /<label> <args>}.
   */
  public void execute(ReactCommandSender sender, String label, String[] args) {
    if (isLanguageCommand(args) && DirectorHosts.command().language(sender, Arrays.copyOfRange(args, 1, args.length))) {
      return;
    }
    if (!(args.length > 0 && args[0].equalsIgnoreCase("debug")
        && (args.length > 1 && args[1].equalsIgnoreCase("dump")
            || args.length == 1 && sender.hasPermission("react.debugdump")))
        && !sender.hasPermission(ROOT_PERMISSION) && !sender.hasPermission("react.*") && !sender.isOp()) {
      ReactLanguage.send(sender, RuntimeMessages.MISSING_PERMISSION,
          MessageArgument.untrusted("permission", ROOT_PERMISSION));
      return;
    }

    LanguageAudience.run(sender.playerId().orElse(null),
        () -> executeCommand(sender, label, normalizeLegacyArgs(args)));
  }

  /**
   * Completes the last word of {@code /<alias> <args>}.
   */
  public List<String> complete(ReactCommandSender sender, String alias, String[] args) {
    if (isLanguageCommand(args)) {
      Optional<List<String>> language = DirectorHosts.command()
          .completeLanguage(sender, Arrays.copyOfRange(args, 1, args.length));
      if (language.isPresent()) {
        return language.get();
      }
    }
    List<String> suggestions = runDirectorTab(sender, alias, args);
    sender.playSound("minecraft:block.amethyst_block.chime", 0.25f, RNG.r.f(0.125f, 1.95f));
    return suggestions;
  }

  static boolean isLanguageCommand(String[] args) {
    return args.length > 0
        && ("language".equalsIgnoreCase(args[0]) || "languages".equalsIgnoreCase(args[0]));
  }

  public static String[] normalizeLegacyArgs(String[] args) {
    if (args == null || args.length != 1) {
      return args;
    }

    String root = args[0];
    if (root == null) {
      return args;
    }

    if (root.equalsIgnoreCase("environment") || root.equalsIgnoreCase("env")) {
      return new String[]{"environment", "info"};
    }

    return args;
  }

  @Override
  public void beforeInvoke(DirectorInvocation invocation, DirectorRuntimeNode node) {
    if (invocation.getSender() instanceof SenderBridge bridge) {
      DirectorContext.touch(new VolmitSender(bridge.sender()));
    }
  }

  @Override
  public void afterInvoke(DirectorInvocation invocation, DirectorRuntimeNode node) {
    DirectorContext.remove();
  }

  private DirectorContextRegistry buildDirectorContexts() {
    DirectorContextRegistry contexts = new DirectorContextRegistry();
    contexts.register(ReactCommandSender.class, (invocation, map) -> {
      if (invocation.getSender() instanceof SenderBridge bridge) {
        return bridge.sender();
      }
      return null;
    });

    for (Map.Entry<Class<?>, DirectorContextHandler<?>> entry : DirectorContextHandler.contextHandlers.entrySet()) {
      registerContextHandler(contexts, entry.getKey(), entry.getValue());
    }

    return contexts;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void registerContextHandler(DirectorContextRegistry contexts, Class<?> type, DirectorContextHandler<?> handler) {
    contexts.register((Class) type, (invocation, map) -> {
      if (invocation.getSender() instanceof SenderBridge bridge) {
        return ((DirectorContextHandler) handler).handle(new VolmitSender(bridge.sender()));
      }

      return null;
    });
  }

  private static void dispatchDirector(DirectorExecutionMode mode, Runnable runnable) {
    // Commands already arrive on the thread that owns the sender; re-queueing breaks Folia region ownership.
    runnable.run();
  }

  private void executeCommand(ReactCommandSender sender, String label, String[] args) {
    if (sendHelpIfRequested(sender, args)) {
      playSuccessSound(sender);
      return;
    }

    DirectorExecutionResult result = runDirector(sender, label, args);

    if (result.isSuccess()) {
      playSuccessSound(sender);
      return;
    }

    playFailureSound(sender);

    if (!result.isHandled()) {
      ReactLanguage.send(sender, RuntimeMessages.UNKNOWN_COMMAND);
    }
  }

  private boolean sendHelpIfRequested(ReactCommandSender sender, String[] args) {
    Optional<DirectorMiniMenu.DirectorHelpPage> page = DirectorMiniMenu.resolveHelp(getDirector(), Arrays.asList(normalizeHelpArgs(args)));
    if (page.isEmpty()) {
      return false;
    }

    DirectorMiniMenu.DirectorHelpPage listed = page.get().withEntries(CommandAvailability.listed(page.get().entries()));
    deliverHelp(sender, listed, DirectorMiniMenu.Theme.reactBlue());
    return true;
  }

  private static void deliverHelp(ReactCommandSender sender, DirectorMiniMenu.DirectorHelpPage page, DirectorMiniMenu.Theme theme) {
    if (!sender.isPlayer()) {
      for (String line : DirectorMiniMenu.renderConsole(page, ReactLanguage.directorResolver())) {
        if (line != null && !line.trim().isEmpty()) {
          sender.sendMessage(ReactLanguage.owned(line));
        }
      }
      return;
    }

    List<String> lines = DirectorMiniMenu.render(page, theme, ReactLanguage.directorResolver());
    boolean renderable = false;
    for (String line : lines) {
      if (line != null && !line.trim().isEmpty()) {
        renderable = true;
        break;
      }
    }
    if (renderable) {
      sender.send(ComponentText.literal("\n".repeat(DirectorMiniMenu.CHAT_LINE_COUNT - 1)));
    }
    for (String line : lines) {
      if (line != null && !line.trim().isEmpty()) {
        sender.send(ComponentText.component(HELP_MARKUP.deserialize(ReactLanguage.owned(line))));
      }
    }
  }

  private String[] normalizeHelpArgs(String[] args) {
    if (args == null || args.length == 0) {
      return new String[0];
    }

    List<String> normalized = new ArrayList<>(args.length);
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (!isHelpWord(arg)) {
        normalized.add(arg);
        continue;
      }

      String page = "1";
      if (i + 1 < args.length && isPageToken(args[i + 1])) {
        page = args[i + 1].trim();
        i++;
      }
      normalized.add("help=" + page);
    }

    return normalized.toArray(new String[0]);
  }

  private boolean isHelpWord(String value) {
    return value != null && (value.equalsIgnoreCase("help") || value.equals("?"));
  }

  private boolean isPageToken(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }

    try {
      Integer.parseInt(value.trim());
      return true;
    } catch (NumberFormatException ignored) {
      return false;
    }
  }

  private DirectorExecutionResult runDirector(ReactCommandSender sender, String label, String[] args) {
    try {
      return getDirector().execute(new DirectorInvocation(new SenderBridge(sender), label, Arrays.asList(args)));
    } catch (Throwable e) {
      ReactPlatforms.get().reportError("Director command execution failed: "
          + e.getClass().getSimpleName() + " " + e.getMessage(), e);
      return DirectorExecutionResult.notHandled();
    }
  }

  private List<String> runDirectorTab(ReactCommandSender sender, String alias, String[] args) {
    try {
      DirectorRuntimeEngine engine = getDirector();
      List<String> suggestions = engine.tabComplete(new DirectorInvocation(new SenderBridge(sender), alias, Arrays.asList(args)));
      return withoutUnlisted(engine.getRoot(), args, suggestions);
    } catch (Throwable e) {
      ReactPlatforms.get().warn("Director tab completion failed: " + e.getClass().getSimpleName()
          + " " + e.getMessage() + ": " + e);
      return List.of();
    }
  }

  static List<String> withoutUnlisted(DirectorRuntimeNode root, String[] args, List<String> suggestions) {
    DirectorRuntimeNode cursor = root;
    for (int index = 0; cursor != null && index < args.length - 1; index++) {
      cursor = child(cursor, args[index]);
    }
    if (cursor == null || suggestions == null || suggestions.isEmpty()) {
      return suggestions;
    }
    List<String> listed = new ArrayList<>(suggestions.size());
    for (String suggestion : suggestions) {
      DirectorRuntimeNode node = child(cursor, suggestion);
      if (node == null || CommandAvailability.listed(node)) {
        listed.add(suggestion);
      }
    }
    return listed;
  }

  private static DirectorRuntimeNode child(DirectorRuntimeNode node, String token) {
    if (token == null) {
      return null;
    }
    for (DirectorRuntimeNode child : node.getChildren()) {
      for (String name : child.allNames()) {
        if (name.equalsIgnoreCase(token)) {
          return child;
        }
      }
    }
    return null;
  }

  private static void playFailureSound(ReactCommandSender sender) {
    sender.playSound("minecraft:block.respawn_anchor.deplete", 0.77f, 0.25f);
    sender.playSound("minecraft:block.beacon.deactivate", 0.2f, 0.45f);
  }

  private static void playSuccessSound(ReactCommandSender sender) {
    sender.playSound("minecraft:block.amethyst_cluster.break", 0.77f, 1.65f);
    sender.playSound("minecraft:block.respawn_anchor.charge", 0.125f, 2.99f);
  }

  private record SenderBridge(ReactCommandSender sender) implements DirectorSender {
    @Override
    public String getName() {
      return sender.name();
    }

    @Override
    public boolean isPlayer() {
      return sender.isPlayer();
    }

    @Override
    public void sendMessage(String message) {
      if (message != null && !message.trim().isEmpty()) {
        sender.send(ComponentText.literal(message));
      }
    }
  }
}
