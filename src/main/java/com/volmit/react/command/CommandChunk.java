package com.volmit.react.command;

import com.volmit.react.Gate;
import com.volmit.react.Info;
import com.volmit.react.api.*;
import com.volmit.react.controller.EventController;
import com.volmit.react.util.F;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import primal.lang.collection.GList;
import primal.lang.collection.GMap;
import primal.util.text.C;

import java.util.List;

public class CommandChunk extends ReactCommand {
    public CommandChunk() {
        command = Info.COMMAND_CHUNK;
        aliases = new String[]{Info.COMMAND_CHUNK_ALIAS_1, Info.COMMAND_CHUNK_ALIAS_2};
        permissions = new String[]{Permissable.ACCESS.getNode(), Permissable.MONITOR.getNode()};
        usage = Info.COMMAND_CHUNK_USAGE;
        description = Info.COMMAND_CHUNK_DESCRIPTION;
        sideGate = SideGate.PLAYERS_ONLY;
    }

    @Override
    public List<String> onTabComplete(CommandSender arg0, Command arg1, String arg2, String[] arg3) {
        GList<String> l = new GList<String>();

        return l;
    }

    @Override
    public void fire(CommandSender sender, String[] args) {
        Chunk c = ((Player) sender).getLocation().getChunk();
        sender.sendMessage(Gate.header("Chunk " + c.getX() + " " + c.getZ(), C.AQUA));

        sender.sendMessage(C.GRAY + "Entities: " + C.WHITE + C.BOLD + c.getEntities().length);
        sender.sendMessage(C.GRAY + "Tile Entities: " + C.WHITE + C.BOLD + c.getTileEntities().length);

        try {
            LagMap lm = EventController.map;

            if (lm.getChunks().containsKey(c)) {
                LagMapChunk lc = lm.getChunks().get(c);
                GMap<ChunkIssue, Double> g = lc.getPercent();

                for (ChunkIssue j : lc.getHits().k()) {
                    sender.sendMessage(C.AQUA + j.toName() + ": " + C.DARK_AQUA + C.BOLD + F.pc(g.get(j), 0) + " Impact: " + F.f((int) (lc.getHits().get(j) / 10000D)));
                }
            }
        } catch (Throwable e) {

        }

        sender.sendMessage(Gate.header(C.AQUA));
    }
}
