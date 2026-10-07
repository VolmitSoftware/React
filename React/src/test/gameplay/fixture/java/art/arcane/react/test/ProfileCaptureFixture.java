package art.arcane.react.test;

import art.arcane.react.React;
import art.arcane.react.api.action.Action;
import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.content.action.ActionCaptureProfile;
import art.arcane.react.util.common.scheduling.ReactExecutors;
import com.google.gson.Gson;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class ProfileCaptureFixture {
    private final JavaPlugin plugin;
    private ActionTicket<ActionCaptureProfile.Params> ticket;
    private ActionTicket<ActionCaptureProfile.Params> overlap;
    private volatile Map<String, Object> inspected = Map.of();

    public ProfileCaptureFixture(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean execute(Player player, String[] args) {
        try {
            switch (args[1]) {
                case "begin" -> begin(3);
                case "long" -> begin(30);
                case "overlap" -> {
                    Action<ActionCaptureProfile.Params> action = React.action(ActionCaptureProfile.ID);
                    overlap = action.create();
                    overlap.queue();
                }
                case "cancel", "cleanup" -> close();
                case "snapshot" -> { }
                default -> throw new IllegalArgumentException("Unknown profile command");
            }
            player.sendMessage("REACT_PROFILE " + args[1] + " " + new Gson().toJson(snapshot()));
        } catch (Exception failure) {
            plugin.getLogger().log(Level.SEVERE, "Profile fixture command failed", failure);
            player.sendMessage("REACT_PROFILE ERROR " + failure.getMessage());
        }
        return true;
    }

    private void begin(int seconds) {
        if (ticket != null && !ticket.isDone()) throw new IllegalStateException("Profile fixture already active");
        Action<ActionCaptureProfile.Params> action = React.action(ActionCaptureProfile.ID);
        if (action == null) throw new IllegalStateException("Profile action is unavailable");
        inspected = Map.of();
        overlap = null;
        ticket = action.create(action.getDefaultParams().setSeconds(seconds));
        ticket.onComplete(completed -> ReactExecutors.async().execute(() -> inspect(completed)));
        ticket.queue();
    }

    private void inspect(ActionTicket<ActionCaptureProfile.Params> completed) {
        try {
            Path file = Path.of(completed.getParams().getProfilePath());
            Map<String, Integer> events = new ConcurrentHashMap<>();
            try (RecordingFile recording = new RecordingFile(file)) {
                while (recording.hasMoreEvents()) {
                    String type = recording.readEvent().getEventType().getName();
                    events.merge(type, 1, Integer::sum);
                }
            }
            inspected = Map.of("path", file.toString(), "bytes", Files.size(file), "events", events,
                    "inspected", true, "local", file.getParent().equals(React.instance.getDataFolder().toPath().resolve("diagnostics/profiles")));
        } catch (Exception failure) {
            plugin.getLogger().log(Level.SEVERE, "Profile fixture could not inspect capture", failure);
            inspected = Map.of("error", failure.toString());
        }
    }

    private Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>(inspected);
        if (ticket != null) {
            result.put("done", ticket.isDone());
            result.put("failed", ticket.isFailed());
            result.put("count", ticket.getCount());
        }
        if (overlap != null) result.put("overlapFailed", overlap.isFailed());
        int recordings = 0;
        for (Recording recording : FlightRecorder.getFlightRecorder().getRecordings()) {
            if (recording.getName().equals("React profile")) recordings++;
        }
        result.put("recordings", recordings);
        return result;
    }

    public void close() {
        if (ticket != null && !ticket.isDone()) ticket.fail(new IllegalStateException("Profile fixture cancelled"));
        if (overlap != null && !overlap.isDone()) overlap.fail(new IllegalStateException("Profile fixture cancelled"));
    }
}
