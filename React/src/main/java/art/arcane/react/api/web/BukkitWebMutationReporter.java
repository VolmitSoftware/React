package art.arcane.react.api.web;

import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class BukkitWebMutationReporter extends AuditWebMutationReporter {
  public BukkitWebMutationReporter(AuditLog auditLog) {
    super(auditLog);
  }

  @Override
  protected void notifyOperators(String actor, String target, String detail) {
    J.s(() -> tellOperators(actor, target, detail));
  }

  private static void tellOperators(String actor, String target, String detail) {
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
}
