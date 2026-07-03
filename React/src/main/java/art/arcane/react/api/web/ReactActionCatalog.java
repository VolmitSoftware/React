package art.arcane.react.api.web;

import art.arcane.react.content.action.ActionUnknown;

import java.util.Set;

public class ReactActionCatalog {

    public static final Set<String> DESTRUCTIVE_IDS = Set.of(
        "purge-entities",
        "purge-chunks",
        "quarantine-hot-chunks",
        "trim-entities-by-age-priority",
        "collect-garbage",
        "incident-playbook"
    );

    public static final Set<String> EXCLUDED_IDS = Set.of(ActionUnknown.ID);

    public static boolean isDestructive(String id) {
        return id != null && DESTRUCTIVE_IDS.contains(id);
    }
}
