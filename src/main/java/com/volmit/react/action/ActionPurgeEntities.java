package com.volmit.react.action;

import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import com.volmit.react.Config;
import com.volmit.react.Gate;
import com.volmit.react.Info;
import com.volmit.react.Lang;
import com.volmit.react.api.Action;
import com.volmit.react.api.ActionState;
import com.volmit.react.api.ActionType;
import com.volmit.react.api.IActionSource;
import com.volmit.react.api.ISelector;
import com.volmit.react.api.SelectionMode;
import com.volmit.react.api.SelectorEntityType;
import com.volmit.react.api.SelectorPosition;
import com.volmit.react.util.AccessCallback;
import com.volmit.react.util.F;
import com.volmit.react.util.FinalInteger;
import com.volmit.react.util.M;
import com.volmit.react.util.Task;

public class ActionPurgeEntities extends Action
{
	private long ms;
	private int lcd;

	public ActionPurgeEntities()
	{
		super(ActionType.PURGE_ENTITIES);

		setNodes(Info.ACTION_PURGE_ENTITIES_TAGS);

		setDefaultSelector(Chunk.class, new AccessCallback<ISelector>()
		{
			@Override
			public ISelector get()
			{
				SelectorPosition sel = new SelectorPosition();
				sel.addAll();

				return sel;
			}
		});

		setDefaultSelector(EntityType.class, new AccessCallback<ISelector>()
		{
			@Override
			public ISelector get()
			{
				SelectorEntityType sel = new SelectorEntityType(SelectionMode.BLACKLIST);
				sel.add(EntityType.PLAYER);

				searching: for(EntityType i : EntityType.values())
				{
					for(String j : Config.ALLOW_PURGE)
					{
						if(i.name().equalsIgnoreCase(j))
						{
							continue searching;
						}
					}

					sel.add(i);
				}

				return sel;
			}
		});
	}

	@Override
	public void enact(IActionSource source, ISelector... selectors)
	{
		FinalInteger total = new FinalInteger(0);
		FinalInteger totalCulled = new FinalInteger(0);
		FinalInteger totalChunked = new FinalInteger(0);
		FinalInteger completed = new FinalInteger(0);
		ms = M.ms();

		int tchu = 0;
		int tent = 0;

		for(ISelector i : selectors)
		{
			if(i.getType().equals(Chunk.class))
			{
				tchu += i.getPossibilities().size();
			}

			if(i.getType().equals(EntityType.class))
			{
				for(Object j : i.getPossibilities())
				{
					if(i.can(j))
					{
						tent++;
					}
				}
			}
		}

		source.sendResponseActing((isForceful() ? "Force " : "") + Lang.getString("action.purge-entities.purging") + tent + Lang.getString("action.purge-entities.type") + ((tent == 0 || tent > 1) ? "s" : "") + Lang.getString("action.purge-entities.of") + ((tent == 0 || tent > 1) ? Lang.getString("action.purge-entities.entities") : Lang.getString("action.purge-entities.entity")) + Lang.getString("action.purge-entities.across") + F.f(tchu) + Lang.getString("action.purge-entities.chunk") + ((tchu > 1 || tchu == 0) ? "s" : "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$

		for(ISelector i : selectors)
		{
			if(i.getType().equals(Chunk.class))
			{
				total.add(i.getPossibilities().size());

				for(Object j : i.getPossibilities())
				{
					if(i.can(j))
					{
						purge(isForceful(), (Chunk) j, new Runnable()
						{
							@Override
							public void run()
							{
								completed.add(1);
								String s = Info.ACTION_PURGE_ENTITIES_STATUS;
								setProgress((double) completed.get() / (double) total.get());
								s = s.replace("$c", F.f(completed.get()));
								s = s.replace("$t", F.f(total.get()));
								s = s.replace("$p", F.pc(getProgress(), 0));
								setStatus(s);
								ms = M.ms();
								totalCulled.add(lcd);

								if(lcd > 0)
								{
									totalChunked.add(1);
								}

								if(completed.get() == total.get())
								{
									completeAction();
									source.sendResponseSuccess(Lang.getString("action.purge-entities.purged") + F.f(totalCulled.get()) + Lang.getString("action.purge-entities.entities-in") + F.f(totalChunked.get()) + Lang.getString("action.purge-entities.chunk") + ((totalChunked.get() > 1 || totalChunked.get() == 0) ? "s" : "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
								}
							}
						}, source, selectors);
					}
				}
			}
		}

		new Task("purger-monitor-callback", 2)
		{
			@Override
			public void run()
			{
				if(M.ms() - ms > 100 && getState().equals(ActionState.RUNNING))
				{
					cancel();
					completeAction();
					source.sendResponseSuccess(Lang.getString("action.purge-entities.purged") + F.f(totalCulled.get()) + Lang.getString("action.purge-entities.entities-in") + F.f(totalChunked.get()) + Lang.getString("action.purge-entities.chunk") + ((totalChunked.get() > 1 || totalChunked.get() == 0) ? "s" : "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
				}
			}
		};
	}

	public void purge(boolean force, Chunk chunk, Runnable cb, IActionSource source, ISelector... selectors)
	{
		boolean nc = false;
		FinalInteger cu = new FinalInteger(0);

		seeking: for(int f = 0; f < chunk.getEntities().length; f++)
		{
			final int k = f;
			Entity i = chunk.getEntities()[f];

			for(ISelector j : selectors)
			{
				if(j.getType().equals(EntityType.class))
				{
					if(!j.can(i.getType()))
					{
						continue seeking;
					}
				}
			}

			nc = true;
			Gate.purgeEntity(i, force);
			cu.add(1);

			if(k == chunk.getEntities().length - 1)
			{
				lcd = cu.get();
				cb.run();
			}
		}

		if(!nc)
		{
			lcd = cu.get();
			cb.run();
		}
	}

	@Override
	public String getNode()
	{
		return "purge-entities";
	}
}
