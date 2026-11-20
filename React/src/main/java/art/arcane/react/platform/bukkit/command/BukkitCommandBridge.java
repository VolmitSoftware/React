package art.arcane.react.platform.bukkit.command;

import art.arcane.react.React;
import art.arcane.react.content.directorcommand.BridgeCommands;
import art.arcane.react.util.plugin.VolmitSender;

public final class BukkitCommandBridge {

    public void status(VolmitSender sender) {
        BridgeCommands.status(sender, React.bridgeRegistry().snapshotHealth());
    }
}
