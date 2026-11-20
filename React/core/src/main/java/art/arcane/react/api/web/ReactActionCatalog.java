package art.arcane.react.api.web;

import art.arcane.react.content.action.ActionCollectGarbage;
import art.arcane.react.content.action.ActionUnknown;

import java.util.Set;

public class ReactActionCatalog {

    public static final Set<String> DESTRUCTIVE_IDS = Set.of(
        "purge-entities",
        "purge-chunks",
        "action-quarantine-hot-chunks",
        "action-trim-entities-by-age-priority",
        ActionCollectGarbage.ID,
        "action-incident-playbook"
    );

    public static final Set<String> EXCLUDED_IDS = Set.of(ActionUnknown.ID);

    public static boolean isDestructive(String id) {
        return id != null && DESTRUCTIVE_IDS.contains(id);
    }
}
