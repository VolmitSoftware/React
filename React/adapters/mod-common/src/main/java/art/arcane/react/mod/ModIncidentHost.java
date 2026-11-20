package art.arcane.react.mod;

import art.arcane.react.core.incident.IncidentRecord;
import art.arcane.react.platform.IncidentHost;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/** Incident records on mods, kept in memory for React Web until the server stops. */
final class ModIncidentHost implements IncidentHost {
    private static final int RETENTION = 512;
    private final Deque<IncidentRecord> records = new ArrayDeque<>();

    @Override
    public synchronized void record(IncidentRecord record) {
        if (record == null) {
            return;
        }
        records.addLast(record);
        while (records.size() > RETENTION) {
            records.removeFirst();
        }
    }

    @Override
    public synchronized List<IncidentRecord> recent(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<IncidentRecord> result = new ArrayList<>(Math.min(limit, records.size()));
        Iterator<IncidentRecord> iterator = records.descendingIterator();
        while (iterator.hasNext() && result.size() < limit) {
            result.add(iterator.next());
        }
        return List.copyOf(result);
    }
}
