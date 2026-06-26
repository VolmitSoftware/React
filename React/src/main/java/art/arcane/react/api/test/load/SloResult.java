package art.arcane.react.api.test.load;

import java.util.List;
import java.util.Map;

public record SloResult(boolean passed, List<String> breaches, Map<String, Double> metrics) {
}
