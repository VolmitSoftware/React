package art.arcane.react.api.web;

import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.localization.MessageArgument;
import io.javalin.http.Context;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class BukkitWebMutationReporter implements WebMutationReporter {
  private static final int MAX_AUDIT_FIELD_LENGTH = 300;

  private final AuditLog auditLog;

  public BukkitWebMutationReporter(AuditLog auditLog) {
    this.auditLog = auditLog;
  }

  @Override
  public void report(Context context, WebMutation mutation) {
    PairingToken token = context.attribute("token");
    String actorId = token == null ? "web" : "web:" + token.tokenId();
    String actorDisplay = displayActor(token);
    String operation = sanitize(mutation.operation());
    String target = sanitize(mutation.target());
    String detail = sanitize(mutation.detail());
    String result = sanitize(mutation.result());
    auditLog.append(actorId, operation, "target=" + target + " detail=" + detail, result);
    J.s(() -> notifyOperators(actorDisplay, target, detail));
  }

  private static void notifyOperators(String actor, String target, String detail) {
    List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
    for (Player player : players) {
      J.runEntity(player, () -> {
        if (!player.isOnline() || !player.isOp()) {
          return;
        }
        ReactLanguage.sendPrefixed(
            player,
            CommandMessages.WEB_MUTATION_NOTICE,
            MessageArgument.untrusted("actor", actor),
            MessageArgument.untrusted("target", target),
            MessageArgument.untrusted("detail", detail)
        );
      });
    }
  }

  static String displayActor(PairingToken token) {
    if (token == null) {
      return "web";
    }
    String label = token.label() == null || token.label().isBlank() ? token.tokenId() : token.label();
    return label + " (" + token.role() + ", " + token.tokenId() + ")";
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
