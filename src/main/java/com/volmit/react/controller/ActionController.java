package com.volmit.react.controller;

import org.bukkit.Chunk;
import org.bukkit.command.CommandSender;

import com.volmit.react.Config;
import com.volmit.react.Gate;
import com.volmit.react.action.ActionCPUScore;
import com.volmit.react.action.ActionChunkTest;
import com.volmit.react.action.ActionCollectGarbage;
import com.volmit.react.action.ActionCullEntities;
import com.volmit.react.action.ActionDump;
import com.volmit.react.action.ActionFileSize;
import com.volmit.react.action.ActionFixLighting;
import com.volmit.react.action.ActionLockFluid;
import com.volmit.react.action.ActionLockHopper;
import com.volmit.react.action.ActionLockRedstone;
import com.volmit.react.action.ActionPullTimings;
import com.volmit.react.action.ActionPurgeChunks;
import com.volmit.react.action.ActionPurgeEntities;
import com.volmit.react.action.ActionUnlockFluid;
import com.volmit.react.action.ActionUnlockHopper;
import com.volmit.react.action.ActionUnlockRedstone;
import com.volmit.react.api.ActionAlreadyRunningException;
import com.volmit.react.api.ActionException;
import com.volmit.react.api.ActionState;
import com.volmit.react.api.ActionType;
import com.volmit.react.api.ConsoleActionSource;
import com.volmit.react.api.IAction;
import com.volmit.react.api.IActionSource;
import com.volmit.react.api.ISelector;
import com.volmit.react.api.Note;
import com.volmit.react.api.PlayerActionSource;
import com.volmit.react.api.RAIActionSource;
import com.volmit.react.api.SelectorPosition;
import com.volmit.react.util.C;
import com.volmit.react.util.Controller;
import com.volmit.react.util.Ex;
import com.volmit.react.util.JSONArray;
import com.volmit.react.util.JSONObject;
import com.volmit.react.util.TICK;
import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GMap;
import com.volmit.volume.lang.collections.GTriset;

public class ActionController extends Controller
{
	private static int kiv = 0;
	private GMap<ActionType, IAction> actions;
	public GMap<Integer, GTriset<ActionType, IActionSource, GList<ISelector>>> pending;
	public GList<String> tasks;
	private GMap<String, GList<ActionType>> rans;

	@Override
	public void dump(JSONObject object)
	{
		JSONArray acts = new JSONArray();
		JSONArray queue = new JSONArray();

		for(ActionType i : actions.k())
		{
			JSONObject a = new JSONObject();
			a.put("name", i.name());
			a.put("description", i.getDescription());
			a.put("handle", i.getHandle().name());
			a.put("target", i.getTarget().name());
			acts.put(i);
		}

		for(Integer i : pending.k())
		{
			JSONObject pend = new JSONObject();
			ActionType t = pending.get(i).getA();
			IActionSource s = pending.get(i).getB();
			GList<ISelector> e = pending.get(i).getC();
			JSONArray sels = new JSONArray();

			for(ISelector j : e)
			{
				JSONObject ss = new JSONObject();
				ss.put("type", j.getClass().getSimpleName());
				ss.put("selected", j.getList().size());
				ss.put("possibility", j.getPossibilities().size());
				ss.put("mode", j.getMode().name());
				sels.put(ss);
			}

			pend.put("id", i);
			pend.put("type", t.name());
			pend.put("source", s.toString());
			pend.put("selectors", sels);
			queue.put(pend);
		}

		object.put("queue", queue);
		object.put("loaded-actions", acts);
	}

	public boolean isAlreadyQueued(ActionType a)
	{
		for(Integer i : pending.k())
		{
			if(pending.get(i).getA().equals(a))
			{
				return true;
			}
		}

		return false;
	}

	@Override
	public void start()
	{
		tasks = new GList<String>();
		pending = new GMap<Integer, GTriset<ActionType, IActionSource, GList<ISelector>>>();
		actions = new GMap<ActionType, IAction>();
		rans = new GMap<String, GList<ActionType>>();

		registerAction(new ActionCollectGarbage());
		registerAction(new ActionPullTimings());
		registerAction(new ActionCullEntities());
		registerAction(new ActionFixLighting());
		registerAction(new ActionLockFluid());
		registerAction(new ActionLockHopper());
		registerAction(new ActionLockRedstone());
		registerAction(new ActionPurgeChunks());
		registerAction(new ActionPurgeEntities());
		registerAction(new ActionUnlockFluid());
		registerAction(new ActionUnlockHopper());
		registerAction(new ActionUnlockRedstone());
		registerAction(new ActionChunkTest());
		registerAction(new ActionCPUScore());
		registerAction(new ActionDump());
		registerAction(new ActionFileSize());
	}

	public void fire(ActionType type, IActionSource source, ISelector... selectors)
	{
		if(source instanceof RAIActionSource && isAlreadyQueued(type))
		{
			return;
		}

		pending.put(kiv++, new GTriset<ActionType, IActionSource, GList<ISelector>>(type, source, new GList<ISelector>(selectors)));
	}

	private boolean fireAction(ActionType type, IActionSource source, ISelector... selectors) throws ActionException
	{
		IAction a = getAction(type);
		boolean failed = false;

		if(a.getState().equals(ActionState.IDLE))
		{
			try
			{
				int d = 0;

				for(ISelector i : selectors)
				{
					if(i.getType().equals(Chunk.class))
					{
						SelectorPosition sel = (SelectorPosition) i;

						for(Object j : new GList<Object>(sel.getPossibilities()))
						{
							Chunk cc = (Chunk) j;

							if(!Config.getWorldConfig(cc.getWorld()).allowActions)
							{
								d++;
								sel.getPossibilities().remove(cc);
							}
						}

						if(i.getPossibilities().isEmpty())
						{
							source.sendResponseError("Action failed. No chunks selected.");
							throw new ActionException();
						}
					}
				}

				if(d > 0)
				{
					source.sendResponseActing("Removed " + d + " chunk(s) from selection (blocked)");
				}

				a.act(source, selectors);
			}

			catch(ActionAlreadyRunningException e)
			{
				failed = true;
			}
		}

		else
		{
			failed = true;
		}

		return !failed;
	}

	public IAction getAction(ActionType type)
	{
		return actions.get(type);
	}

	public void registerAction(IAction action)
	{
		actions.put(action.getType(), action);
	}

	@Override
	public void stop()
	{

	}

	@Override
	public void tick()
	{
		try
		{
			Gate.tickDeath();
		}

		catch(Throwable e)
		{

		}

		if(TICK.tick % 100 == 0)
		{
			for(String i : rans.k())
			{
				GMap<ActionType, Integer> cts = new GMap<ActionType, Integer>();

				for(ActionType j : rans.get(i))
				{
					if(!cts.containsKey(j))
					{
						cts.put(j, 0);
					}

					cts.put(j, cts.get(j) + 1);
				}

				String s = C.WHITE + i + C.GRAY + " ran";

				for(ActionType j : cts.k())
				{
					s += " " + (cts.get(j) > 1 ? (C.GRAY.toString() + cts.get(j) + "x " + C.WHITE) : C.WHITE + "") + j.getName() + C.GRAY;
				}

				Note.ACTION.bake(s);
			}

			rans.clear();
		}

		Gate.snd = 3;

		if(pending.isEmpty())
		{
			return;
		}

		GMap<ActionType, Integer> pendingStatus = new GMap<ActionType, Integer>();
		GMap<ActionType, String> runningStatus = new GMap<ActionType, String>();

		for(int d : pending.k())
		{
			GTriset<ActionType, IActionSource, GList<ISelector>> i = pending.get(d);
			IAction action = getAction(i.getA());
			IActionSource source = i.getB();
			ISelector[] selectors = i.getC().toArray(new ISelector[i.getC().size()]);
			boolean running = action.getState().equals(ActionState.IDLE);

			try
			{
				boolean ran = running ? fireAction(i.getA(), source, selectors) : false;

				if(ran)
				{
					String src = source.toString();

					if(!rans.containsKey(src))
					{
						rans.put(src, new GList<ActionType>());
					}

					rans.get(src).add(i.getA());
					pending.remove(d);
				}

				if(!pendingStatus.containsKey(i.getA()))
				{
					pendingStatus.put(i.getA(), 0);
				}

				pendingStatus.put((i.getA()), pendingStatus.get((i.getA())) + 1);
			}

			catch(ActionException e)
			{
				pending.remove(d);
			}
		}

		for(ActionType i : ActionType.values())
		{
			try
			{
				if(getAction(i).getState().equals(ActionState.RUNNING))
				{
					if(!pendingStatus.containsKey(i))
					{
						pendingStatus.put(i, 0);
					}

					pendingStatus.put((i), pendingStatus.get((i)) + 1);
					runningStatus.put(i, getAction(i).getStatus());
				}
			}

			catch(Throwable e)
			{
				Ex.t(e);
			}
		}

		tasks.clear();

		for(ActionType i : pendingStatus.k())
		{
			String pre = pendingStatus.get(i) > 1 ? pendingStatus.get(i) + "x " : "";

			if(getAction(i).getState().equals(ActionState.RUNNING))
			{
				pre += getAction(i).getStatus();
			}

			else
			{
				pre += i.getName();
			}

			tasks.add(pre);
		}
	}

	public GList<IAction> getActions()
	{
		return actions.v();
	}

	public GList<String> getActionNames()
	{
		GList<String> acts = new GList<String>();

		for(IAction i : getActions())
		{
			acts.add(i.getName());
		}

		return acts;
	}

	public void displayQueue(CommandSender sender)
	{
		int m = pending.size();
		GMap<String, Integer> p = new GMap<String, Integer>();

		for(Integer i : pending.k())
		{
			GTriset<ActionType, IActionSource, GList<ISelector>> v = pending.get(i);
			ActionType t = v.getA();
			IActionSource s = v.getB();
			String b = s instanceof PlayerActionSource ? ((PlayerActionSource) s).getPlayer().getName() : s instanceof ConsoleActionSource ? "Console" : "React";
			String l = t.getName() + " by " + b;

			if(!p.containsKey(l))
			{
				p.put(l, 0);
			}

			p.put(l, p.get(l) + 1);
		}

		for(String i : p.k())
		{
			Gate.msgActing(sender, i + (p.get(i) > 1 ? "x" + p.get(i) : ""));
		}

		Gate.msg(sender, m + " actions are queued to run.");
	}

	public void clearQueue(CommandSender sender)
	{
		pending.clear();
	}

	@Override
	public int getInterval()
	{
		return 7;
	}

	@Override
	public boolean isUrgent()
	{
		return false;
	}
}
