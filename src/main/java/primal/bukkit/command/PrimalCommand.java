package primal.bukkit.command;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

import primal.lang.collection.GList;

/**
 * Represents a pawn command
 *
 * @author cyberpwn
 *
 */
public abstract class PrimalCommand implements ICommand
{
	private GList<PrimalCommand> children;
	private GList<String> nodes;
	private GList<String> requiredPermissions;
	private String node;
	private String category;

	/**
	 * Override this with a super constructor as most commands shouldnt change these
	 * parameters
	 *
	 * @param node
	 *            the node (primary node) i.e. volume
	 * @param nodes
	 *            the aliases. i.e. v, vol, bile
	 */
	public PrimalCommand(String node, String... nodes)
	{
		category = "";
		this.node = node;
		this.nodes = new GList<String>(nodes);
		requiredPermissions = new GList<>();
		children = buildChildren();
	}

	protected void requiresPermission(PrimalPermission node)
	{
		if(node == null)
		{
			return;
		}

		requiresPermission(node.toString());
	}

	protected void requiresPermission(String node)
	{
		if(node == null)
		{
			return;
		}

		requiredPermissions.add(node);
	}

	@Override
	public String getNode()
	{
		return node;
	}

	@Override
	public GList<String> getNodes()
	{
		return nodes;
	}

	@Override
	public GList<String> getAllNodes()
	{
		return getNodes().copy().qadd(getNode());
	}

	@Override
	public void addNode(String node)
	{
		getNodes().add(node);
	}

	public GList<PrimalCommand> getChildren()
	{
		return children;
	}

	private GList<PrimalCommand> buildChildren()
	{
		GList<PrimalCommand> p = new GList<>();

		for(Field i : getClass().getDeclaredFields())
		{
			if(i.isAnnotationPresent(Command.class))
			{
				try
				{
					i.setAccessible(true);
					PrimalCommand pc = (PrimalCommand) i.getType().getConstructor().newInstance();
					Command c = i.getAnnotation(Command.class);

					if(!c.value().trim().isEmpty())
					{
						pc.setCategory(c.value().trim());
					}

					else
					{
						pc.setCategory(getCategory());
					}

					p.add(pc);
				}

				catch(IllegalArgumentException | IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException | SecurityException e)
				{
					e.printStackTrace();
				}
			}
		}

		return p;
	}

	@Override
	public GList<String> getRequiredPermissions()
	{
		return requiredPermissions;
	}

	public String getCategory()
	{
		return category;
	}

	public void setCategory(String category)
	{
		this.category = category;
	}
}
