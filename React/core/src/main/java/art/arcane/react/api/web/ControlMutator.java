package art.arcane.react.api.web;

@FunctionalInterface
public interface ControlMutator {
    boolean apply(String path, Object value);
}
