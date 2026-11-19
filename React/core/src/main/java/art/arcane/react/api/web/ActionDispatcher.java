package art.arcane.react.api.web;

import java.util.Map;

public interface ActionDispatcher {
    record DispatchResult(String ticketId, String status) {}

    DispatchResult dispatch(String actionId, Map<String, Object> params);
}
