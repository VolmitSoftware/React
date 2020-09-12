package com.volmit.react.api;

import com.volmit.react.controller.MonitorController;

import primal.lang.collection.GList;
import primal.util.text.C;

public class TitleMonitor
{
	private GList<MonitorHeading> headings;

	public TitleMonitor()
	{
		headings = new GList<MonitorHeading>();
	}

	public void addHeading(MonitorHeading h)
	{
		headings.add(h);
	}

	public int getMaxSelection()
	{
		return headings.size() - 1;
	}

	public int left(int sel)
	{
		sel = Math.min(sel, getMaxSelection());
		sel = Math.max(sel, 0);

		if(sel == 0)
		{
			return getMaxSelection();
		}

		return sel - 1;
	}

	public int right(int sel)
	{
		sel = Math.min(sel, getMaxSelection());
		sel = Math.max(sel, 0);

		if(sel == getMaxSelection())
		{
			return 0;
		}

		return sel + 1;
	}

	public MonitorHeading getHeadFor(int sel)
	{
		return headings.get(sel);
	}

	public String getHotbarHeadFor(int sel, boolean b, MonitorController mc, ReactPlayer rp, int cd)
	{
		StringBuilder m = new StringBuilder();

		if(!b)
		{
			return m.toString();
		}

		for(ISampler i : getHeadFor(sel).getChildren())
		{
			String cx = mc.prefixForSub(rp, i.getColor(), cd);
			m.append(" " + C.RESET).append(cx).append(C.stripColor(i.get()));
		}

		if(m.length() < 2)
		{
			return m.toString();
		}

		return m.substring(1);
	}

	public String getHotbarFor(int sel, boolean b, ReactPlayer rp)
	{
		StringBuilder m = new StringBuilder();
		int sl = 0;

		for(MonitorHeading i : headings)
		{
			String pd = C.DARK_GRAY.toString();
			String po = i.getHead().getColor().toString();
			String pc = (sel == -1 || sl == sel) ? po : pd;
			m.append(" " + C.RESET).append(pc).append(i.getHead().get());
			sl++;
		}

		if(m.length() < 2)
		{
			return m.toString();
		}

		return m.substring(1);
	}

	public String getConsoleHotbar()
	{
		StringBuilder m = new StringBuilder();
		int sl = 0;
		int sel = -1;

		for(MonitorHeading i : headings)
		{
			String pd = C.DARK_GRAY.toString();
			String po = i.getHead().getColor().toString();
			String pc = (sel == -1 || sl == sel) ? po : pd;
			m.append(" " + C.RESET).append(pc).append(i.getHead().get());
			sl++;
		}

		if(m.length() < 2)
		{
			return m.toString();
		}

		return m.substring(1);
	}
}
