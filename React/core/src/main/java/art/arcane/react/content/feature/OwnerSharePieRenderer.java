package art.arcane.react.content.feature;

import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.platform.MapRenderTarget;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Each owner's share of one measure, such as one kind of cost, chunks held or network traffic. Values arrive in the
 * unit's base: microseconds for ms/s, otherwise whole chunks, bytes or samples.
 */
public final class OwnerSharePieRenderer extends SharePieRenderer {
  private static final double MICROS_PER_MILLI = 1000D;
  private static final double MINIMUM_SLICE_SHARE = 0.02D;

  public enum Unit {
    MS_PER_SECOND(RendererMessages.PIE_UNIT_MS_PER_SECOND),
    CHUNKS(RendererMessages.PIE_UNIT_CHUNKS),
    BYTES_PER_SECOND(RendererMessages.PIE_UNIT_BYTES_PER_SECOND),
    SAMPLES(RendererMessages.PIE_UNIT_SAMPLES);

    private final TextKey label;

    Unit(TextKey label) {
      this.label = label;
    }
  }

  private final TextKey title;
  private final TinyColor header;
  private final Unit unit;
  private final Supplier<Map<String, Long>> buckets;
  private final TextKey empty;

  public OwnerSharePieRenderer(String id, TextKey title, TinyColor header, Unit unit, Supplier<Map<String, Long>> buckets, TextKey empty) {
    super(id);
    this.title = title;
    this.header = header;
    this.unit = unit;
    this.buckets = buckets;
    this.empty = empty;
  }

  @Override
  protected TextKey title() {
    return title;
  }

  @Override
  protected TinyColor headerColor() {
    return header;
  }

  @Override
  protected TextKey totalUnit() {
    return unit.label;
  }

  @Override
  protected TextKey emptyMessage() {
    return empty;
  }

  @Override
  protected double minimumSliceShare() {
    return MINIMUM_SLICE_SHARE;
  }

  @Override
  protected boolean viewerAvailable(MapRenderTarget target) {
    return true;
  }

  @Override
  protected Map<String, Long> collectBuckets(MapRenderTarget target) {
    Map<String, Long> values = buckets.get();
    return values == null ? Map.of() : values;
  }

  @Override
  protected String compact(long value) {
    return unit == Unit.MS_PER_SECOND ? AnimusCostSharePieRenderer.formatMillis(value) : compactCount(value);
  }

  @Override
  protected String compactShort(long value) {
    if (unit != Unit.MS_PER_SECOND) {
      return compactCountShort(value);
    }
    double millis = Math.max(0L, value) / MICROS_PER_MILLI;
    return Form.f(millis, millis >= 10D ? 0 : 1);
  }
}
