package com.volmit.react.fix;

import org.bukkit.Chunk;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import com.volmit.react.Gate;
import com.volmit.react.React;
import com.volmit.react.api.ActionType;
import com.volmit.react.api.Capability;
import com.volmit.react.api.PlayerActionSource;
import com.volmit.react.api.SelectorPosition;
import com.volmit.react.util.S;

public class FixInvisibleChunk extends Fix implements Listener
{
	boolean running;

	public FixInvisibleChunk()
	{
		running = false;
		setName("Fix Invisible Chunk");
		setId("ghost-chunk");
		setAliases(new String[] {"gchunk", "chunk"});
		setDescription("Re-sends chunk packets in this chunk.");
		setUsage("[-brutal]");
	}

	@SuppressWarnings("deprecation")
	@Override
	public void run(CommandSender sender, String[] args)
	{
		if(!Capability.CHUNK_RELIGHTING.isCapable())
		{
			Capability.CHUNK_RELIGHTING.sendNotCapable(sender);
			return;
		}

		if(sender instanceof Player)
		{
			Player p = (Player) sender;
			Chunk ch = p.getLocation().getChunk();

			for(String i : args)
			{
				if(i.equalsIgnoreCase("-brutal"))
				{
					ch.getWorld().refreshChunk(ch.getX(), ch.getZ());
					Gate.msgActing(sender, "Force Refreshed (brutality)");
					break;
				}
			}

			new S("kdel")
			{
				@Override
				public void run()
				{
					SelectorPosition pos = new SelectorPosition();
					pos.add(ch);
					React.instance.actionController.fire(ActionType.FIX_LIGHTING, new PlayerActionSource(p), pos);
					Gate.msgActing(sender, "Attempting to resend this chunk to nearby players.");
				}
			};
		}

		else
		{
			Gate.msgError(sender, "Consoles cant have a position. Use /re a fix-lighting @c:<chunk> instead");
		}
	}
}
