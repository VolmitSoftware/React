package art.arcane.react.localization.catalog;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

public final class ActionMessages {
  public static final TextKey COMPLETED = TextKey.of("action.completed", "Completed {action} in {duration}");
  public static final TextKey PURGED_CHUNKS = TextKey.of("action.purge_chunks.completed", "Purged {chunks} chunks in {duration}");
  public static final TextKey PREWARMED_CHUNKS = TextKey.of("action.prewarm_chunks.completed", "Prewarmed {warmed} chunks ({loaded} newly loaded) in {duration}");
  public static final TextKey TRIMMED_ENTITIES = TextKey.of("action.trim_entities.completed", "Trimmed {entities} entities across {chunks} chunks in {duration}");
  public static final TextKey GC_NO_RECLAIM = TextKey.of("action.collect_garbage.no_reclaim", "Garbage collection completed with no immediate heap reclaimed ({used} used) in {duration}");
  public static final TextKey GC_RECLAIMED = TextKey.of("action.collect_garbage.reclaimed", "Freed {freed} ({before} -> {after}) in {duration}");
  public static final TextKey PLAYBOOK_QUEUED = TextKey.of("action.incident_playbook.completed", "Playbook tier {tier} queued {actions} mitigation actions in {duration}");
  public static final TextKey PURGED_ENTITIES = TextKey.of("action.purge_entities.completed", "Purged {entities} entities across {chunks} chunks in {duration}");
  public static final TextKey NORMALIZED_HOPPERS = TextKey.of("action.hopper_network.completed", "Normalized {hoppers} hoppers, merged {items} item entities, and unloaded {chunks} chunks in {duration}");
  public static final TextKey QUARANTINED_CHUNKS = TextKey.of("action.quarantine_chunks.completed", "Quarantined {chunks} chunks and culled {entities} entities in {duration}");

  private ActionMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    builder.add(COMPLETED);
    builder.add(PURGED_CHUNKS);
    builder.add(PREWARMED_CHUNKS);
    builder.add(TRIMMED_ENTITIES);
    builder.add(GC_NO_RECLAIM);
    builder.add(GC_RECLAIMED);
    builder.add(PLAYBOOK_QUEUED);
    builder.add(PURGED_ENTITIES);
    builder.add(NORMALIZED_HOPPERS);
    builder.add(QUARANTINED_CHUNKS);
  }
}
