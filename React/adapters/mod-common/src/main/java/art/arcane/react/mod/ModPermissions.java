package art.arcane.react.mod;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * React permission nodes on mod servers. A permission mod grants a node through the vanilla permission atom named by
 * the node with its first dot as the namespace separator ({@code react.use} is {@code react:use}). Without a grant, the
 * nodes Bukkit gives everyone stay open and the rest need the gamemaster operator level.
 */
final class ModPermissions {
    private static final Set<String> EVERYONE = Set.of("react.language.self", "volmit.language.self");
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");

    private ModPermissions() {
    }

    static boolean has(CommandSourceStack source, String node) {
        if (node != null && EVERYONE.contains(node)) {
            return true;
        }
        return VanillaApi.granted(source, atom(node).orElse(null)) || VanillaApi.gamemaster(source);
    }

    static boolean has(ServerPlayer player, String node) {
        if (node != null && EVERYONE.contains(node)) {
            return true;
        }
        return VanillaApi.granted(player, atom(node).orElse(null)) || VanillaApi.gamemaster(player);
    }

    static Optional<String> atom(String node) {
        if (node == null) {
            return Optional.empty();
        }
        int dot = node.indexOf('.');
        if (dot <= 0 || dot == node.length() - 1) {
            return Optional.empty();
        }
        String namespace = node.substring(0, dot);
        String path = node.substring(dot + 1);
        if (!NAMESPACE.matcher(namespace).matches() || !PATH.matcher(path).matches()) {
            return Optional.empty();
        }
        return Optional.of(namespace + ":" + path);
    }
}
