package art.arcane.react.content.directorcommand;

import art.arcane.react.api.web.ReactWebServer;
import art.arcane.react.api.web.profiler.ProfileDtos;
import art.arcane.react.api.web.profiler.SnapshotStore;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.util.director.DirectorExecutor;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** {@code /react snapshot profile|list}: saves the current profile and lists saved profile and heatmap snapshots. */
@Director(
    name = "snapshot",
    aliases = {"snap"},
    origin = DirectorOrigin.BOTH,
    description = "Save and list snapshots",
    descriptionKey = "command.description.snapshot"
)
public class CommandSnapshot implements DirectorExecutor {
  private static final int LISTED = 10;

  @Director(
      name = "profile",
      description = "Save the current profile as a snapshot",
      descriptionKey = "command.description.snapshot.profile"
  )
  public void profile(
      @Param(name = "label", description = "Label for the snapshot", descriptionKey = "command.parameter.snapshot.label", defaultValue = "", aliases = {"l"})
      String label
  ) {
    VolmitSender sender = sender();
    Optional<ReactWebServer> web = CommandProfiler.web(sender, "snapshot profile");
    if (web.isEmpty()) {
      return;
    }
    if (label != null && label.strip().length() > SnapshotStore.MAX_LABEL) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.WEB_INVALID_LABEL);
      return;
    }
    ProfileDtos.Profile profile = web.get().profiler().profile();
    if (profile.profiler().samples() == 0) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.SNAPSHOT_NO_SAMPLES);
      return;
    }
    SnapshotStore.Meta meta;
    try {
      meta = web.get().snapshots().save(SnapshotStore.PROFILE, label, web.get().serverName(), SnapshotStore.profileSummary(profile), profile);
    } catch (IOException failure) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.SNAPSHOT_FAILED, MessageArgument.untrusted("reason", String.valueOf(failure.getMessage())));
      return;
    }
    ReactLanguage.sendPrefixed(sender, CommandMessages.SNAPSHOT_SAVED,
        MessageArgument.untrusted("id", meta.id()),
        MessageArgument.untrusted("label", meta.label()),
        MessageArgument.untrusted("summary", meta.summary()));
  }

  @Director(
      name = "list",
      aliases = {"ls"},
      description = "List saved snapshots",
      descriptionKey = "command.description.snapshot.list"
  )
  public void list() {
    VolmitSender sender = sender();
    Optional<ReactWebServer> web = CommandProfiler.web(sender, "snapshot list");
    if (web.isEmpty()) {
      return;
    }
    List<SnapshotStore.Meta> snapshots = web.get().snapshots().list(null);
    if (snapshots.isEmpty()) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.SNAPSHOT_EMPTY);
      return;
    }
    ReactLanguage.sendPrefixed(sender, CommandMessages.SNAPSHOT_HEADER, MessageArgument.untrusted("count", snapshots.size()));
    long now = System.currentTimeMillis();
    for (SnapshotStore.Meta meta : snapshots.subList(0, Math.min(LISTED, snapshots.size()))) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.SNAPSHOT_ENTRY,
          MessageArgument.untrusted("id", meta.id()),
          MessageArgument.untrusted("type", meta.kind()),
          MessageArgument.untrusted("label", meta.label()),
          MessageArgument.untrusted("age", Form.duration(Math.max(0L, now - meta.createdAt()), 0)),
          MessageArgument.untrusted("summary", meta.summary()));
    }
  }
}
