package art.arcane.react.api.web.profiler;

import java.util.List;

/** The JSON shapes of {@code /api/v1/profiler} and the profile snapshots that freeze them. */
public final class ProfileDtos {
  private ProfileDtos() {
  }

  public record State(boolean running, int intervalMs, String threads, Long startedAt, Long stopsAt, long samples,
                      long durationMs) {
  }

  public record MethodCount(String method, long samples) {
  }

  public record OwnerRank(String id, String name, long samples, double percent, List<MethodCount> top) {
  }

  public record Owners(long samples, List<OwnerRank> owners) {
  }

  public record HotMethod(String method, String owner, String ownerName, long self, long total, double selfPercent,
                          double totalPercent) {
  }

  public record Hot(long samples, List<HotMethod> methods) {
  }

  public record Node(String method, String owner, String ownerName, long total, long self, List<Node> children) {
  }

  public record Tree(long samples, List<Node> roots) {
  }

  public record ThreadSamples(String name, String state, long samples, double percent) {
  }

  public record Threads(List<ThreadSamples> threads) {
  }

  /** A frozen profile: everything the live endpoints answer, at the moment the snapshot was taken. */
  public record Profile(State profiler, Owners owners, Hot hot, Threads threads, Tree tree) {
  }
}
