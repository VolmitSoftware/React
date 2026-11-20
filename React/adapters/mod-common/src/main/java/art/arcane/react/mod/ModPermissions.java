package art.arcane.react.mod;

import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;

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

    static boolean has(PermissionSet permissions, String node) {
        if (node != null && EVERYONE.contains(node)) {
            return true;
        }
        Identifier atom = atom(node).map(Identifier::tryParse).orElse(null);
        if (atom != null && permissions.hasPermission(Permission.Atom.create(atom))) {
            return true;
        }
        return permissions.hasPermission(Permissions.COMMANDS_GAMEMASTER);
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
