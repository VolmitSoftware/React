package art.arcane.react.api.web;

@FunctionalInterface
public interface ConsoleCommandDispatcher {

    boolean dispatch(String command);
}
