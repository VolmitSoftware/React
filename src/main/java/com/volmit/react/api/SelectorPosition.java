package com.volmit.react.api;

import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.volmit.react.util.Ex;
import com.volmit.react.util.W;
import com.volmit.volume.lang.collections.GSet;

public class SelectorPosition extends Selector
{
	public SelectorPosition()
	{
		super(Chunk.class, SelectionMode.BLACKLIST);
	}

	public void add(Chunk c)
	{
		getPossibilities().add(c);
	}

	public void add(Chunk c, int rad)
	{
		getPossibilities().addAll(W.chunkRadius(c, rad));
	}

	public void add(World w)
	{
		for(Chunk i : w.getLoadedChunks())
		{
			add(i);
		}
	}

	public void addAll()
	{
		for(World i : Bukkit.getWorlds())
		{
			add(i);
		}
	}

	@Override
	public int parse(CommandSender sender, String input) throws SelectorParseException
	{
		GSet<Chunk> ch = new GSet<Chunk>();

		if(input.contains("&&"))
		{
			throw new SelectorParseException("Cannot use \"&&\". This isnt java :P");
		}

		if(input.contains("&"))
		{
			for(String i : input.split("&"))
			{
				boolean neg = i.startsWith("!");

				for(Chunk j : parseNode(sender, neg ? i.substring(1) : i))
				{
					if(neg)
					{
						ch.remove(j);
					}

					else
					{
						ch.add(j);
					}
				}
			}
		}

		else
		{
			if(input.startsWith("!"))
			{
				throw new SelectorParseException("Cannot negate from nothing. Use <something>&!<somethingElse>");
			}

			for(Chunk j : parseNode(sender, input))
			{
				ch.add(j);
			}
		}

		getPossibilities().addAll(ch);

		return ch.size();
	}

	public GSet<Chunk> parseNode(CommandSender sender, String input) throws SelectorParseException
	{
		GSet<Chunk> chunks = new GSet<Chunk>();

		if(input.startsWith("this"))
		{
			int rad = 0;
			Chunk c = null;

			if(sender instanceof Player)
			{
				c = ((Player) sender).getLocation().getChunk();
			}

			else
			{
				throw new SelectorParseException("Only players can use the \"this\" keyword. We don't know where you are!");
			}

			if(input.contains("+"))
			{
				try
				{
					rad = Integer.valueOf(input.split("\\+")[1]);
				}

				catch(NumberFormatException e)
				{
					throw new SelectorParseException("Unable to parse integer: " + input.split("+")[1]);
				}

				catch(Throwable e)
				{
					Ex.t(e);

					throw new SelectorParseException("Unable to parse: " + input);
				}
			}

			if(rad != 0)
			{
				if(rad < 0)
				{
					throw new SelectorParseException("Chunk Radius must be positive (" + input + ")");
				}

				for(Chunk i : W.chunkRadius(c, rad))
				{
					chunks.add(i);
				}
			}

			else
			{
				chunks.add(c);
			}
		}

		if(input.startsWith("look"))
		{
			int rad = 0;
			Chunk c = null;

			if(sender instanceof Player)
			{
				c = ((Player) sender).getTargetBlock((Set<Material>) null, 512).getChunk();
			}

			else
			{
				throw new SelectorParseException("Only players can use the \"look\" keyword. We don't know where you are!");
			}

			if(input.contains("+"))
			{
				try
				{
					rad = Integer.valueOf(input.split("\\+")[1]);
				}

				catch(NumberFormatException e)
				{
					throw new SelectorParseException("Unable to parse integer: " + input.split("+")[1]);
				}

				catch(Throwable e)
				{
					Ex.t(e);
					throw new SelectorParseException("Unable to parse: " + input);
				}
			}

			if(rad != 0)
			{
				if(rad < 0)
				{
					throw new SelectorParseException("Chunk Radius must be positive (" + input + ")");
				}

				for(Chunk i : W.chunkRadius(c, rad))
				{
					chunks.add(i);
				}
			}

			else
			{
				chunks.add(c);
			}
		}

		else if(input.equals("*"))
		{
			for(World i : Bukkit.getWorlds())
			{
				for(Chunk j : i.getLoadedChunks())
				{
					chunks.add(j);
				}
			}
		}

		else if(input.endsWith(".*"))
		{
			String wsearch = input.substring(0, input.length() - 2);
			boolean found = false;

			searching: for(World i : Bukkit.getWorlds())
			{
				if(i.getName().equalsIgnoreCase(wsearch))
				{
					found = true;

					for(Chunk j : i.getLoadedChunks())
					{
						chunks.add(j);
					}

					break searching;
				}
			}

			if(!found)
			{
				throw new SelectorParseException("Unable to locate world " + wsearch);
			}
		}

		else if(input.contains(".") && input.contains(","))
		{
			String wsearch = input.split("\\.")[0];

			boolean found = false;

			searching: for(World i : Bukkit.getWorlds())
			{
				if(i.getName().equalsIgnoreCase(wsearch))
				{
					found = true;

					String chunk = input.split("\\.")[1];
					String subs = null;

					if(chunk.contains("+"))
					{
						subs = chunk.split("\\+")[1];
						chunk = chunk.split("\\+")[0];
					}

					if(!chunk.contains(","))
					{
						throw new SelectorParseException("Cannot identify chunk coords for \"" + chunk + "\" Should be <x>,<z>");
					}

					int x;
					int z;

					try
					{
						x = Integer.valueOf(chunk.split(",")[0]);
					}

					catch(NumberFormatException e)
					{
						throw new SelectorParseException("Cannot identify x value for chunk selector: " + chunk.split(",")[0]);
					}

					try
					{
						z = Integer.valueOf(chunk.split(",")[1]);
					}

					catch(NumberFormatException e)
					{
						throw new SelectorParseException("Cannot identify z value for chunk selector: " + chunk.split(",")[1]);
					}

					if(i.isChunkLoaded(x, z))
					{
						Chunk cc = i.getChunkAt(x, z);

						if(subs != null)
						{
							try
							{
								Integer rad = Integer.valueOf(subs.substring(1));

								if(rad < 1)
								{
									throw new SelectorParseException("Chunk Radius must be 1 or higher");
								}

								chunks.addAll(W.chunkRadius(cc, rad));
							}

							catch(NumberFormatException e)
							{
								throw new SelectorParseException("Unable to parse radius for chunk slector: " + subs.substring(1));
							}
						}

						else
						{
							chunks.add(cc);
						}
					}

					break searching;
				}
			}

			if(!found)
			{
				throw new SelectorParseException("Unable to locate world " + wsearch);
			}
		}

		return chunks;
	}

	@Override
	public String getName()
	{
		return "position";
	}
}
