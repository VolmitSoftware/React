package art.arcane.react.mod;

import art.arcane.react.content.feature.SharePieRenderer;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.platform.MapRenderTarget;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.Map;

/** Each mod's rolling share of world generation time, beside the mod impact pie. */
final class ModWorldgenPieRenderer extends SharePieRenderer {
    static final String ID = "worldgen-mod-pie-map";
    private static final TinyColor HEADER = new TinyColor(86, 140, 62);
    private final ModOwnerCost ownerCost;

    ModWorldgenPieRenderer(ModOwnerCost ownerCost) {
        super(ID);
        this.ownerCost = ownerCost;
    }

    @Override
    protected TextKey title() {
        return RendererMessages.TITLE_MOD_WORLDGEN_SHARE;
    }

    @Override
    protected TinyColor headerColor() {
        return HEADER;
    }

    @Override
    protected TextKey totalUnit() {
        return RendererMessages.PIE_UNIT_IMPACT;
    }

    @Override
    protected boolean viewerAvailable(MapRenderTarget target) {
        return true;
    }

    @Override
    protected Map<String, Long> collectBuckets(MapRenderTarget target) {
        ownerCost.markSamplerActivity();
        return ownerCost.worldgenShare();
    }
}
