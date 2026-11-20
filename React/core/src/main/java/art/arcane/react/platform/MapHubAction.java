package art.arcane.react.platform;

public sealed interface MapHubAction {
  record OpenHub() implements MapHubAction {
  }

  record OpenGroup(int groupOrder, int page) implements MapHubAction {
  }

  record SelectMap(String rendererId, String rendererName, String scopeTag) implements MapHubAction {
  }

  record AddMap(String rendererId, String rendererName, String scopeTag) implements MapHubAction {
  }
}
