package com.volmit.react.api;

import com.volmit.react.controller.MonitorController;
import com.volmit.react.util.C;
import com.volmit.volume.lang.collections.GList;

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
		sel = sel > getMaxSelection() ? getMaxSelection() : sel;
		sel = sel < 0 ? 0 : sel;

		if(sel == 0)
		{
			return getMaxSelection();
		}

		return sel - 1;
	}

	public int right(int sel)
	{
		sel = sel > getMaxSelection() ? getMaxSelection() : sel;
		sel = sel < 0 ? 0 : sel;

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
		String m = "";

		if(!b)
		{
			return m;
		}

		for(ISampler i : getHeadFor(sel).getChildren())
		{
			String cx = mc.prefixForSub(rp, i.getColor(), cd);
			m += " " + C.RESET + cx + "" + C.stripColor(i.get());
		}

		if(m.length() < 2)
		{
			return m;
		}

		return m.substring(1);
	}

	public String getHotbarFor(int sel, boolean b, ReactPlayer rp)
	{
		String m = "";
		int sl = 0;

		for(MonitorHeading i : headings)
		{
			String pd = C.DARK_GRAY.toString();
			String po = i.getHead().getColor().toString();
			String pc = (sel == -1 || sl == sel) ? po : pd;
			m += " " + C.RESET + pc + i.getHead().get();
			sl++;
		}

		if(m.length() < 2)
		{
			return m;
		}

		return m.substring(1);
	}
}
