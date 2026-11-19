package art.arcane.react.api.web;

import io.javalin.http.Context;

@FunctionalInterface
public interface WebMutationReporter {
  void report(Context context, WebMutation mutation);
}
