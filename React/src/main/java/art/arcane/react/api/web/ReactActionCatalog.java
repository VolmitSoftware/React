package art.arcane.react.api.web;

import art.arcane.react.content.action.ActionCollectGarbage;
import art.arcane.react.content.action.ActionIncidentPlaybook;
import art.arcane.react.content.action.ActionPurgeChunks;
import art.arcane.react.content.action.ActionPurgeEntities;
import art.arcane.react.content.action.ActionQuarantineHotChunks;
import art.arcane.react.content.action.ActionTrimEntitiesByAgePriority;
import art.arcane.react.content.action.ActionUnknown;

import java.util.Set;

public class ReactActionCatalog {

    public static final Set<String> DESTRUCTIVE_IDS = Set.of(
        ActionPurgeEntities.ID,
        ActionPurgeChunks.ID,
        ActionQuarantineHotChunks.ID,
        ActionTrimEntitiesByAgePriority.ID,
        ActionCollectGarbage.ID,
        ActionIncidentPlaybook.ID
    );

    public static final Set<String> EXCLUDED_IDS = Set.of(ActionUnknown.ID);

    public static boolean isDestructive(String id) {
        return id != null && DESTRUCTIVE_IDS.contains(id);
    }
}
