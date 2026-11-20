package art.arcane.react.content.directorcommand;

import art.arcane.react.platform.ReactCommandSender;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.volmlib.util.plugin.ComponentText;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class RecordingSender implements ReactCommandSender {
  final List<String> lines = new ArrayList<>();

  @Override
  public String name() {
    return "console";
  }

  @Override
  public boolean isPlayer() {
    return false;
  }

  @Override
  public Optional<ReactPlayer> player() {
    return Optional.empty();
  }

  @Override
  public boolean hasPermission(String node) {
    return true;
  }

  @Override
  public boolean isOp() {
    return true;
  }

  @Override
  public void send(ComponentText message) {
    lines.add(message.plain());
  }

  @Override
  public void sendMessage(String message) {
    lines.add(message);
  }

  @Override
  public Object handle() {
    return this;
  }
}
