package art.arcane.react.api.rendering;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.awt.Color;

public class MapColorsTest {
    @Test
    public void colorForCachesNearestPaletteColorBySourceRgb() {
        Color first = MapColors.colorFor(0x123456);
        Color second = MapColors.colorFor(0x123456);

        Assertions.assertSame(first, second);
        Assertions.assertEquals(255, first.getAlpha());
    }

    @Test
    public void colorForIgnoresAlphaBits() {
        Color withoutAlpha = MapColors.colorFor(0x123456);
        Color withAlpha = MapColors.colorFor(0xFF123456);

        Assertions.assertSame(withoutAlpha, withAlpha);
    }
}
