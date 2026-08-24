package art.arcane.react.web;

import art.arcane.react.api.web.ControlSerializer;
import art.arcane.react.api.web.ControlMutator;
import art.arcane.react.api.web.RegistryControlBackend;
import art.arcane.react.util.project.config.ConfigDoc;
import art.arcane.react.util.project.registry.Registered;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RegistryControlBackendTest {

    @Test
    void rejectedEnabledMutationDoesNotReturnAFalseSuccess() {
        TestControl control = new TestControl();
        RegistryControlBackend<TestControl> backend = backend(control, (path, value) -> false);

        assertThrows(IllegalStateException.class, () -> backend.setEnabled(control.getId(), true));
    }

    @Test
    void rejectedKnobBatchRestoresAlreadyAppliedValues() {
        TestControl control = new TestControl();
        List<String> calls = new ArrayList<>();
        RegistryControlBackend<TestControl> backend = backend(control, (path, value) -> {
            calls.add(path + "=" + value);
            return !path.endsWith(".second");
        });
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("first", 10);
        updates.put("second", 20);

        assertThrows(IllegalStateException.class, () -> backend.setKnobs(control.getId(), updates));
        assertEquals(List.of(
            "feature.test-control.first=10",
            "feature.test-control.second=20",
            "feature.test-control.first=1"
        ), calls);
    }

    private static RegistryControlBackend<TestControl> backend(
            TestControl control,
            ControlMutator mutator) {
        return new RegistryControlBackend<>(
            "feature",
            () -> List.of(control),
            id -> control.getId().equals(id) ? control : null,
            item -> item.enabled,
            new ControlSerializer(),
            mutator
        );
    }

    private static final class TestControl implements Registered {
        private boolean enabled;
        @ConfigDoc(value = "First value.")
        private int first = 1;
        @ConfigDoc(value = "Second value.")
        private int second = 2;

        @Override
        public String getId() {
            return "test-control";
        }
    }
}
