package art.arcane.react.content.feature;

import art.arcane.react.content.feature.perworld.WorldBudgetOverride;
import art.arcane.react.util.project.config.TomlCodec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PerWorldTickBudgetOverrideTest {

    @Test
    void defaultsReturnGlobalValuesWhenNoOverride() {
        FeaturePerWorldTickBudget feature = new FeaturePerWorldTickBudget();
        Assertions.assertEquals(35.0, feature.effectiveBudgetMsFor("minecraft:overworld"), 1e-9);
        Assertions.assertEquals(50.0, feature.effectivePanicMsFor("minecraft:overworld"), 1e-9);
        Assertions.assertEquals(28.0, feature.effectiveReleaseMsFor("minecraft:overworld"), 1e-9);
    }

    @Test
    void setWorldOverrideSnapshotsGlobalAndAppliesSpecifiedFields() {
        FeaturePerWorldTickBudget feature = new FeaturePerWorldTickBudget();
        WorldBudgetOverride override = feature.setWorldOverride("minecraft:the_nether", 40.0, null, null);
        Assertions.assertEquals(40.0, override.getBudgetMs(), 1e-9);
        Assertions.assertEquals(50.0, override.getPanicMs(), 1e-9);
        Assertions.assertEquals(28.0, override.getReleaseMs(), 1e-9);
        Assertions.assertEquals(40.0, feature.effectiveBudgetMsFor("minecraft:the_nether"), 1e-9);
        Assertions.assertEquals(35.0, feature.effectiveBudgetMsFor("minecraft:overworld"), 1e-9);
    }

    @Test
    void setWorldOverrideUpdatesOnlySpecifiedFieldsOnSecondCall() {
        FeaturePerWorldTickBudget feature = new FeaturePerWorldTickBudget();
        feature.setWorldOverride("minecraft:the_nether", 40.0, null, null);
        WorldBudgetOverride override = feature.setWorldOverride("minecraft:the_nether", null, 70.0, null);
        Assertions.assertEquals(40.0, override.getBudgetMs(), 1e-9);
        Assertions.assertEquals(70.0, override.getPanicMs(), 1e-9);
        Assertions.assertEquals(28.0, override.getReleaseMs(), 1e-9);
    }

    @Test
    void overrideMapSurvivesTomlRoundTrip() throws Exception {
        FeaturePerWorldTickBudget feature = new FeaturePerWorldTickBudget();
        feature.setWorldOverride("minecraft:the_nether", 40.0, null, null);

        String toml = TomlCodec.toToml(feature, "feature:per-world-tick-budget");
        FeaturePerWorldTickBudget reloaded = TomlCodec.fromToml(toml, FeaturePerWorldTickBudget.class);
        reloaded.onActivate();

        Assertions.assertEquals(40.0, reloaded.effectiveBudgetMsFor("minecraft:the_nether"), 1e-9);
        reloaded.onDeactivate();
    }
}
