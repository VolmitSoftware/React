package art.arcane.react.content.feature;

import art.arcane.react.api.rendering.MapTheme;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.rendering.Region;
import art.arcane.react.api.rendering.RendererLayout;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.MapMessages;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.platform.MapAnchorHost;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;
import java.util.function.Supplier;

/**
 * Animus packet kinds ranked by bandwidth.
 */
public class AnimusNetworkListRenderer implements ReactRenderer {
  public static final String ID = "animus-network-list-map";
  private static final TinyColor ACCENT = new TinyColor(58, 142, 158);
  private final Supplier<AnimusMetrics.Snapshot> metrics;
  private final MapAnchorHost anchors;

  public AnimusNetworkListRenderer(Supplier<AnimusMetrics.Snapshot> metrics, MapAnchorHost anchors) {
    this.metrics = metrics;
    this.anchors = anchors;
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public MegamapGrid.MegamapCapability megamapCapability() {
    return MegamapGrid.MegamapCapability.adaptiveWall();
  }

  @Override
  public void render() {
    AnimusMetrics.Snapshot snapshot = metrics.get();
    RendererLayout.backdrop(this, MapTheme.SURFACE_0, MapTheme.SURFACE_1);
    dashHeader(
        ReactLanguage.raw(RendererMessages.TITLE_ANIMUS_NETWORK),
        anchors.frameAnchored(target()) ? ReactLanguage.raw(RendererMessages.STATUS_FRAME) : topLabel(snapshot),
        ACCENT,
        MapTheme.ACCENT_YELLOW
    );

    Region body = RendererLayout.body(this);
    List<AnimusMetrics.Entry> entries = snapshot.packetKinds();
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
    double maxRate = entries.get(0).value();
    double kindTotal = 0D;
    for (AnimusMetrics.Entry entry : entries) {
      kindTotal += entry.value();
    }

    for (int i = 0; i < limit; i++) {
      Region row = list.rowAt(i, rowHeight);
      if (!visible(row)) {
        continue;
      }

      AnimusMetrics.Entry entry = entries.get(i);
      double normalized = maxRate <= 0D ? 0D : Math.max(0D, Math.min(1D, entry.value() / maxRate));
      double share = kindTotal <= 0D ? 0D : entry.value() / kindTotal;
      RendererLayout.listRow(this, i, row.withoutBottom(gutter), weights, gutter, normalized, columnValues(columns, i, share, entry));
    }

    RendererLayout.footer(this, packetRateLabel(snapshot), playerPeakLabel(snapshot), ACCENT);
  }

  private void drawEmptyState(Region body, AnimusMetrics.Snapshot snapshot) {
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

  private String[] columnValues(int columns, int rank, double share, AnimusMetrics.Entry entry) {
    String name = "#" + (rank + 1) + " " + entry.displayName();
    String rate = RendererLayout.compact(entry.value());
    return columns == 1
        ? new String[]{name, rate}
        : new String[]{name, rate, RendererLayout.percent(share)};
  }

  private String topLabel(AnimusMetrics.Snapshot snapshot) {
    return Double.isFinite(snapshot.bytesPerSecond())
        ? valueLabel(RendererMessages.ANIMUS_BANDWIDTH, snapshot.bytesPerSecond())
        : ReactLanguage.raw(RendererMessages.STATUS_WAITING);
  }

  private String packetRateLabel(AnimusMetrics.Snapshot snapshot) {
    return Double.isFinite(snapshot.packetsPerSecond())
        ? valueLabel(RendererMessages.ANIMUS_PACKET_RATE, snapshot.packetsPerSecond())
        : null;
  }

  private String playerPeakLabel(AnimusMetrics.Snapshot snapshot) {
    return Double.isFinite(snapshot.playerPeakBytesPerSecond())
        ? valueLabel(RendererMessages.ANIMUS_PLAYER_PEAK, snapshot.playerPeakBytesPerSecond())
        : null;
  }

  private String valueLabel(TextKey key, double value) {
    return ReactLanguage.raw(key, MessageArgument.untrusted("value", RendererLayout.compact(value)));
  }

}
