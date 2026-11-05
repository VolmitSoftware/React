package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.CapabilityGatedFeature;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.api.rendering.MapTheme;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.rendering.Region;
import art.arcane.react.api.rendering.RendererLayout;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.MapMessages;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;
import java.util.Set;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for the Animus Network List Map feature.")
public class FeatureAnimusNetworkListMap extends ReactFeature implements ReactRenderer, CapabilityGatedFeature {
  public static final String ID = "animus-network-list-map";
  private static final TinyColor ACCENT = new TinyColor(58, 142, 158);

  public FeatureAnimusNetworkListMap() {
    super(ID);
  }

  @Override
  public MegamapGrid.MegamapCapability megamapCapability() {
    return MegamapGrid.MegamapCapability.adaptiveWall();
  }

  @Override
  public void render() {
    AnimusMetricSeries.Snapshot snapshot = AnimusMetricSeries.snapshot();
    RendererLayout.backdrop(this, MapTheme.SURFACE_0, MapTheme.SURFACE_1);
    dashHeader(
        ReactLanguage.raw(RendererMessages.TITLE_ANIMUS_NETWORK),
        isFrameAnchored() ? ReactLanguage.raw(RendererMessages.STATUS_FRAME) : topLabel(snapshot),
        ACCENT,
        MapTheme.ACCENT_YELLOW
    );

    Region body = RendererLayout.body(this);
    List<AnimusMetricSeries.Entry> entries = snapshot.packetKinds();
    if (entries.isEmpty()) {
      drawEmptyState(body, snapshot);
      RendererLayout.footer(this, null, null, ACCENT);
      return;
    }

    int columns = megamapColumns();
    int[] weights = columnWeights(columns);
    int gutter = MapTheme.gutter(uiScale());
    Region list = body;
    if (megamapDetail().atLeast(MegamapGrid.MegamapDetail.EXPANDED)) {
      Region strip = body.topBand(MapTheme.lineHeight(uiScale()));
      RendererLayout.columnLabels(this, strip, strip.splitColumns(gutter, weights), columnLabels(columns));
      list = body.withoutTop(strip.height() + gutter);
    }

    int rowHeight = RendererLayout.listRowHeight(this);
    int limit = Math.min(entries.size(), Math.max(1, list.rowCapacity(rowHeight)));
    double maxRate = entries.getFirst().value();
    double kindTotal = 0D;
    for (AnimusMetricSeries.Entry entry : entries) {
      kindTotal += entry.value();
    }

    for (int i = 0; i < limit; i++) {
      Region row = list.rowAt(i, rowHeight);
      if (!visible(row)) {
        continue;
      }

      AnimusMetricSeries.Entry entry = entries.get(i);
      double normalized = maxRate <= 0D ? 0D : Math.max(0D, Math.min(1D, entry.value() / maxRate));
      double share = kindTotal <= 0D ? 0D : entry.value() / kindTotal;
      RendererLayout.listRow(this, i, row.withoutBottom(gutter), weights, gutter, normalized, columnValues(columns, i, share, entry));
    }

    RendererLayout.footer(this, packetRateLabel(snapshot), playerPeakLabel(snapshot), ACCENT);
  }

  private void drawEmptyState(Region body, AnimusMetricSeries.Snapshot snapshot) {
    if (!snapshot.declared()) {
      RendererLayout.emptyState(
          this,
          body,
          ReactLanguage.raw(RendererMessages.ANIMUS_UNAVAILABLE),
          ReactLanguage.raw(RendererMessages.ANIMUS_UPDATE_HINT)
      );
      return;
    }

    RendererLayout.emptyState(
        this,
        body,
        ReactLanguage.raw(RendererMessages.ANIMUS_NO_TRAFFIC),
        ReactLanguage.raw(RendererMessages.ANIMUS_TRAFFIC_HINT)
    );
  }

  private int[] columnWeights(int columns) {
    return switch (columns) {
      case 1 -> new int[]{64, 36};
      case 2, 3, 4 -> new int[]{50, 25, 25};
      default -> new int[]{60, 20, 20};
    };
  }

  private String[] columnLabels(int columns) {
    String name = ReactLanguage.raw(MapMessages.COLUMN_NAME);
    String rate = ReactLanguage.raw(MapMessages.COLUMN_BANDWIDTH);
    return columns == 1
        ? new String[]{name, rate}
        : new String[]{name, rate, ReactLanguage.raw(MapMessages.COLUMN_SHARE)};
  }

  private String[] columnValues(int columns, int rank, double share, AnimusMetricSeries.Entry entry) {
    String name = "#" + (rank + 1) + " " + entry.displayName();
    String rate = RendererLayout.compact(entry.value());
    return columns == 1
        ? new String[]{name, rate}
        : new String[]{name, rate, RendererLayout.percent(share)};
  }

  private String topLabel(AnimusMetricSeries.Snapshot snapshot) {
    return Double.isFinite(snapshot.bytesPerSecond())
        ? valueLabel(RendererMessages.ANIMUS_BANDWIDTH, snapshot.bytesPerSecond())
        : ReactLanguage.raw(RendererMessages.STATUS_WAITING);
  }

  private String packetRateLabel(AnimusMetricSeries.Snapshot snapshot) {
    return Double.isFinite(snapshot.packetsPerSecond())
        ? valueLabel(RendererMessages.ANIMUS_PACKET_RATE, snapshot.packetsPerSecond())
        : null;
  }

  private String playerPeakLabel(AnimusMetricSeries.Snapshot snapshot) {
    return Double.isFinite(snapshot.playerPeakBytesPerSecond())
        ? valueLabel(RendererMessages.ANIMUS_PLAYER_PEAK, snapshot.playerPeakBytesPerSecond())
        : null;
  }

  private String valueLabel(TextKey key, double value) {
    return ReactLanguage.raw(key, MessageArgument.untrusted("value", RendererLayout.compact(value)));
  }

  private boolean isFrameAnchored() {
    MapController mapController = React.controller(MapController.class);
    return mapController != null && mapController.hasFrameAnchor(view());
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of(AnimusMetricSeries.SOURCE_ID);
  }
}
