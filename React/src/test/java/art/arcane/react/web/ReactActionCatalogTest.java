package art.arcane.react.web;

import art.arcane.react.api.web.ReactActionCatalog;
import art.arcane.react.content.action.ActionCollectGarbage;
import art.arcane.react.content.action.ActionHopperNetworkNormalize;
import art.arcane.react.content.action.ActionIncidentPlaybook;
import art.arcane.react.content.action.ActionPrewarmCriticalChunks;
import art.arcane.react.content.action.ActionPurgeChunks;
import art.arcane.react.content.action.ActionPurgeEntities;
import art.arcane.react.content.action.ActionQuarantineHotChunks;
import art.arcane.react.content.action.ActionTrimEntitiesByAgePriority;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReactActionCatalogTest {

    @Test
    void destructiveGateUsesCanonicalRuntimeActionIds() {
        assertTrue(ReactActionCatalog.isDestructive(ActionPurgeEntities.ID));
        assertTrue(ReactActionCatalog.isDestructive(ActionPurgeChunks.ID));
        assertTrue(ReactActionCatalog.isDestructive(ActionCollectGarbage.ID));
        assertTrue(ReactActionCatalog.isDestructive(ActionQuarantineHotChunks.ID));
        assertTrue(ReactActionCatalog.isDestructive(ActionTrimEntitiesByAgePriority.ID));
        assertTrue(ReactActionCatalog.isDestructive(ActionIncidentPlaybook.ID));
        assertFalse(ReactActionCatalog.isDestructive(ActionHopperNetworkNormalize.ID));
        assertFalse(ReactActionCatalog.isDestructive(ActionPrewarmCriticalChunks.ID));
    }
}
