package art.arcane.react.mod;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * React permission nodes on mod servers. A permission mod grants a node through the vanilla permission atom named by
 * the node with its first dot as the namespace separator ({@code react.use} is {@code react:use}). A wildcard node such as
 * {@code react.*} or {@code react.shorthands.*} is the atom with {@code all} in place of the star ({@code react:all},
 * {@code react:shorthands.all}) and grants every node below it, as the wildcards do on Bukkit. Without a grant, the
 * nodes Bukkit gives everyone stay open and the rest need the gamemaster operator level. The owner of a singleplayer
 * world holds every node, with or without cheats, as the owner of a Bukkit server does from its console.
 */
final class ModPermissions {
    private static final Set<String> EVERYONE = Set.of("react.language.self", "volmit.language.self");
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");
    private static final String WILDCARD = "all";

    private ModPermissions() {
    }

    /** Only an integrated server has an owner, and only the player who opened the world is it. */
    static boolean worldOwner(boolean dedicatedServer, boolean singleplayerOwner) {
        return !dedicatedServer && singleplayerOwner;
    }

    static boolean has(CommandSourceStack source, String node) {
        if (node != null && EVERYONE.contains(node)) {
            return true;
        }
        for (String atom : atoms(node)) {
            if (VanillaApi.granted(source, atom)) {
                return true;
            }
        }
        return VanillaApi.gamemaster(source) || source.getEntity() instanceof ServerPlayer player && VanillaApi.worldOwner(player);
    }

    static boolean has(ServerPlayer player, String node) {
        if (node != null && EVERYONE.contains(node)) {
            return true;
        }
        for (String atom : atoms(node)) {
            if (VanillaApi.granted(player, atom)) {
                return true;
            }
        }
        return VanillaApi.gamemaster(player) || VanillaApi.worldOwner(player);
    }

    /**
     * The atoms that grant a node: its own, then each enclosing wildcard from the nearest out, so {@code react.shorthands.gms}
     * is granted by {@code react:shorthands.gms}, {@code react:shorthands.all} and {@code react:all}.
     */
    static List<String> atoms(String node) {
        List<String> atoms = new ArrayList<>();
        if (node == null) {
            return atoms;
        }
        String concrete = node.endsWith(".*") ? node.substring(0, node.length() - 2) + "." + WILDCARD : node;
        atom(concrete).ifPresent(atoms::add);
        String base = node.endsWith(".*") ? node.substring(0, node.length() - 2) : node;
        for (int dot = base.lastIndexOf('.'); dot > 0; dot = base.lastIndexOf('.', dot - 1)) {
            String wildcard = base.substring(0, dot) + "." + WILDCARD;
            if (!atoms.contains(wildcard.replaceFirst("\\.", ":"))) {
                atom(wildcard).ifPresent(atoms::add);
            }
        }
        return atoms;
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
