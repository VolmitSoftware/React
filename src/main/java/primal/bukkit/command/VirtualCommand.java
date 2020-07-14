package primal.bukkit.command;

import java.lang.reflect.Field;

import org.bukkit.command.CommandSender;

import primal.bukkit.sched.J;
import primal.lang.collection.GList;
import primal.lang.collection.GMap;
import primal.util.reflection.V;
import primal.util.text.C;

/**
 * Represents a virtual command. A chain of iterative processing through
 * subcommands.
 *
 * @author cyberpwn
 *
 */
public class VirtualCommand
{
	private ICommand command;
	private String tag;

	private GMap<GList<String>, VirtualCommand> children;

	public VirtualCommand(ICommand command)
	{
		this(command, "");
	}

	public VirtualCommand(ICommand command, String tag)
	{
		this.command = command;
		children = new GMap<GList<String>, VirtualCommand>();
		this.tag = tag;

		for(Field i : command.getClass().getDeclaredFields())
		{
			if(i.isAnnotationPresent(Command.class))
			{
				try
				{
					Command cc = i.getAnnotation(Command.class);
					ICommand cmd = (ICommand) i.getType().getConstructor().newInstance();
					new V(command, true, true).set(i.getName(), cmd);
					children.put(cmd.getAllNodes(), new VirtualCommand(cmd, cc.value().trim().isEmpty() ? tag : cc.value().trim()));
				}

				catch(Exception e)
				{
					e.printStackTrace();
				}
			}
		}
	}

	public String getTag()
	{
		return tag;
	}

	public ICommand getCommand()
	{
		return command;
	}

	public GMap<GList<String>, VirtualCommand> getChildren()
	{
		return children;
	}

	public boolean hit(CommandSender sender, GList<String> chain)
	{
		PrimalSender vs = new PrimalSender(sender);
		vs.setTag(tag);

		if(chain.isEmpty())
		{
			if(!checkPermissions(sender, command))
			{
				return true;
			}

			return command.handle(vs, new String[0]);
		}

		String nl = chain.get(0);

		for(GList<String> i : children.k())
		{
			for(String j : i)
			{
				if(j.equalsIgnoreCase(nl))
				{
					VirtualCommand cmd = children.get(i);
					GList<String> c = chain.copy();
					c.remove(0);
					if(cmd.hit(sender, c))
					{
						return true;
					}
				}
			}
		}

		if(!checkPermissions(sender, command))
		{
			return true;
		}

		return command.handle(vs, chain.toArray(new String[chain.size()]));
	}

	private boolean checkPermissions(CommandSender sender, ICommand command2)
	{
		boolean failed = false;

		for(String i : command.getRequiredPermissions())
		{
			if(!sender.hasPermission(i))
			{
				failed = true;
				J.s(() -> sender.sendMessage("- " + C.WHITE + i));
			}
		}

		if(failed)
		{
			sender.sendMessage("Insufficient Permissions");
			return false;
		}

		return true;
	}
}
