package art.arcane.react.api.web;

import java.util.Locale;
import java.util.Set;

public enum WebRole {

    VIEWER("viewer", Set.of("read")),
    OPERATOR("operator", Set.of("read", "op:execute")),
    ADMIN("admin", Set.of("read", "op:execute", "admin"));

    public static final String DEFAULT_ROLE_ID = "admin";

    private final String roleId;
    private final Set<String> roleScopes;

    WebRole(String id, Set<String> scopes) {
        this.roleId = id;
        this.roleScopes = scopes;
    }

    public String id() {
        return roleId;
    }

    public Set<String> scopes() {
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

    public static Set<String> scopesFor(String roleId) {
        String effective = roleId == null ? DEFAULT_ROLE_ID : roleId;
        WebRole r = fromId(effective);
        return r == null ? Set.of() : r.scopes();
    }

    public static String resolveRoleId(String roleId) {
        return roleId == null ? DEFAULT_ROLE_ID : roleId;
    }
}
