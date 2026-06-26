package art.arcane.react.api.test;

import java.util.Map;

public record TestCheck(String subsystem, String name, TestStatus status, String detail, Map<String, Object> data) {
}
