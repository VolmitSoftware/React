package com.volmit.react.xrai;

import com.volmit.react.React;
import com.volmit.react.api.ActionType;
import com.volmit.react.api.ChunkIssue;
import com.volmit.react.api.ISelector;
import com.volmit.react.api.RAIActionSource;
import com.volmit.react.api.SelectionMode;
import com.volmit.react.api.SelectorEntityType;
import com.volmit.react.api.SelectorParseException;
import com.volmit.react.api.SelectorPosition;
import com.volmit.react.api.SelectorTime;
import com.volmit.react.util.JSONObject;
import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GMap;

public class VirtualAction
{
	private ActionType actionType;
	private GMap<String, String> options;

	public VirtualAction(ActionType at)
	{
		this.actionType = at;
		options = new GMap<String, String>();
	}

	public VirtualAction(JSONObject j)
	{
		this(ActionType.valueOf(j.getString("type").toUpperCase()));

		for(String i : j.keySet())
		{
			if(i.equalsIgnoreCase("type"))
			{
				continue;
			}

			options.put(i, j.get(i).toString());
		}
	}

	public void execute()
	{
		GList<ISelector> selectors = new GList<ISelector>();

		if(options.containsKey("near"))
		{
			SelectorPosition s = new SelectorPosition();
			ChunkIssue ci = ChunkIssue.valueOf(options.get("near").toUpperCase());

			if(options.containsKey("range"))
			{
				int r = Integer.valueOf(options.get("range"));
				s.add(Finder.high(ci), r);
			}

			else
			{
				s.add(Finder.high(ci));
			}

			selectors.add(s);
		}

		if(options.containsKey("time"))
		{
			SelectorTime st = new SelectorTime();

			try
			{
				st.set((long) st.parse(null, options.get("time")));
			}

			catch(SelectorParseException e)
			{
				e.printStackTrace();
			}
		}

		if(options.containsKey("entity"))
		{
			SelectorEntityType et = new SelectorEntityType(SelectionMode.WHITELIST);

			try
			{
				et.parse(null, options.get("entity"));
				selectors.add(et);
			}

			catch(SelectorParseException e)
			{
				e.printStackTrace();
			}
		}

		React.instance.actionController.fire(getActionType(), new RAIActionSource(), selectors.toArray(new ISelector[selectors.size()]));
	}

	public JSONObject toJSON()
	{
		JSONObject j = new JSONObject();

		j.put("type", getActionType().name().toLowerCase());

		for(String i : options.k())
		{
			j.put(i, options.get(i));
		}

		return j;
	}

	public ActionType getActionType()
	{
		return actionType;
	}

	public void setActionType(ActionType actionType)
	{
		this.actionType = actionType;
	}

	public GMap<String, String> getOptions()
	{
		return options;
	}

	public void setOptions(GMap<String, String> options)
	{
		this.options = options;
	}
}
