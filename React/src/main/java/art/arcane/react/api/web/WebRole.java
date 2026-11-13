package art.arcane.react.api.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The pairing roles: shorthands for a set of scopes. */
public enum WebRole {

    VIEWER("viewer", List.of("read")),
    OPERATOR("operator", List.of("read", "op:execute")),
    ADMIN("admin", List.of("read", "op:execute", "admin", "console:read", "console:execute"));

    /** Every scope, in the order pairing links and listings show them. */
    public static final List<String> SCOPES = List.of("read", "op:execute", "admin", "console:read", "console:execute");
    /** What a pairing gets when it names neither a role nor scopes: everything except admin and the console. */
    public static final WebRole DEFAULT = OPERATOR;
    public static final String CUSTOM = "custom";

    private final String roleId;
    private final List<String> roleScopes;

    WebRole(String id, List<String> scopes) {
        this.roleId = id;
        this.roleScopes = scopes;
    }

    public String id() {
        return roleId;
    }

    public List<String> scopes() {
        return roleScopes;
    }

    public static WebRole fromId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (WebRole role : values()) {
            if (role.roleId.equals(normalized)) {
                return role;
            }
        }
        return null;
    }

    /** The role whose scopes are exactly these, or {@link #CUSTOM}. */
    public static String roleOf(Collection<String> scopes) {
        Set<String> given = Set.copyOf(scopes);
        for (WebRole role : values()) {
            if (given.equals(Set.copyOf(role.roleScopes))) {
                return role.roleId;
            }
        }
        return CUSTOM;
    }

    /** {@code a,b} into known scopes in {@link #SCOPES} order; throws naming the first unknown one. */
    public static List<String> parseScopes(String list) {
        Set<String> given = new LinkedHashSet<>();
        for (String part : list.split(",")) {
            String scope = part.trim().toLowerCase(Locale.ROOT);
            if (scope.isEmpty()) {
                continue;
            }
            if (!SCOPES.contains(scope)) {
                throw new IllegalArgumentException(scope);
            }
            given.add(scope);
        }
        return ordered(given);
    }

    /** The scopes in {@link #SCOPES} order, unknown ones dropped. */
    public static List<String> ordered(Collection<String> scopes) {
        List<String> ordered = new ArrayList<>();
        for (String scope : SCOPES) {
            if (scopes.contains(scope)) {
                ordered.add(scope);
            }
        }
        return List.copyOf(ordered);
    }
}
