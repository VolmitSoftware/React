package com.volmit.react.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

import com.volmit.volume.lang.collections.GList;

/**
 * Slate implementation
 *
 * @author cyberpwn
 */
public class PhantomSlate implements Slate
{
	private GList<String> lines;
	protected GList<Player> viewers;
	private String name;
	private Scoreboard slate;

	/**
	 * Create a new slate
	 *
	 * @param name
	 *            the name of the slate
	 */
	public PhantomSlate(String name)
	{
		this.name = name;
		this.lines = new GList<String>();
		this.viewers = new GList<Player>();

		build();
	}

	@Override
	public GList<String> getLines()
	{
		return lines;
	}

	@Override
	public void set(int index, String line)
	{
		lines.set(index, line);
	}

	@Override
	public void setLines(GList<String> lines)
	{
		this.lines = lines;
	}

	@Override
	public String get(int index)
	{
		return lines.hasIndex(index) ? lines.get(index) : null;
	}

	@Override
	public void update()
	{
		build();

		for(Player i : viewers)
		{
			i.setScoreboard(slate);
		}
	}

	@Override
	public void setName(String name)
	{
		this.name = name;
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public void build()
	{
		this.slate = SlateUtil.buildSlate(name, lines);
	}

	@Override
	public void addViewer(Player p)
	{
		if(!isViewing(p))
		{
			viewers.add(p);
		}
	}

	@Override
	public void removeViewer(Player p)
	{
		viewers.remove(p);
		p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
	}

	@Override
	public GList<Player> getViewers()
	{
		return viewers.copy();
	}

	@Override
	public boolean isViewing(Player p)
	{
		return viewers.contains(p);
	}

	@Override
	public void addLine(String s)
	{
		lines.add(s);
	}

	@Override
	public void clearLines()
	{
		lines.clear();
	}
}