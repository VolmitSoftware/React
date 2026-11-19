package art.arcane.react.api.web;

import art.arcane.react.api.web.pairing.WebToken;
import io.javalin.http.Context;

/** Writes every web change to the audit log, then lets the platform tell its operators. */
public class AuditWebMutationReporter implements WebMutationReporter {
  private static final int MAX_AUDIT_FIELD_LENGTH = 300;

  private final AuditLog auditLog;

  public AuditWebMutationReporter(AuditLog auditLog) {
    this.auditLog = auditLog;
  }

  @Override
  public void report(Context context, WebMutation mutation) {
    WebToken token = context.attribute("token");
    String actorId = token == null ? "web" : "web:" + token.id();
    String actorDisplay = displayActor(token);
    String operation = sanitize(mutation.operation());
    String target = sanitize(mutation.target());
    String detail = sanitize(mutation.detail());
    String result = sanitize(mutation.result());
    auditLog.append(actorId, operation, "target=" + target + " detail=" + detail, result);
    notifyOperators(actorDisplay, target, detail);
  }

  /** Called after the audit line; tells online operators. Does nothing where the platform has no chat to tell. */
  protected void notifyOperators(String actor, String target, String detail) {
  }

  static String displayActor(WebToken token) {
    if (token == null) {
      return "web";
    }
    String label = token.label() == null || token.label().isBlank() ? token.id() : token.label();
    return label + " (" + token.role() + ", " + token.id() + ")";
  }

  private static String sanitize(String value) {
    if (value == null) {
      return "";
    }
    String cleaned = value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
    return cleaned.length() <= MAX_AUDIT_FIELD_LENGTH
        ? cleaned
        : cleaned.substring(0, MAX_AUDIT_FIELD_LENGTH);
  }
}
