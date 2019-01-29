package com.volmit.react.action;

import org.bukkit.Chunk;

import com.volmit.react.Gate;
import com.volmit.react.Info;
import com.volmit.react.Lang;
import com.volmit.react.api.Action;
import com.volmit.react.api.ActionState;
import com.volmit.react.api.ActionType;
import com.volmit.react.api.IActionSource;
import com.volmit.react.api.ISelector;
import com.volmit.react.api.SelectorPosition;
import com.volmit.react.util.AccessCallback;
import com.volmit.react.util.F;
import com.volmit.react.util.FinalInteger;
import com.volmit.react.util.M;
import com.volmit.react.util.S;
import com.volmit.react.util.Task;

public class ActionPurgeChunks extends Action
{
	private long ms;
	private int lcd;
	private boolean fail;

	public ActionPurgeChunks()
	{
		super(ActionType.PURGE_CHUNKS);
		fail = false;
		setNodes(Info.ACTION_PURGE_CHUNKS_TAGS);

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
	}

	@Override
	public void enact(IActionSource source, ISelector... selectors)
	{
		FinalInteger total = new FinalInteger(0);
		FinalInteger totalCulled = new FinalInteger(0);
		FinalInteger totalChunked = new FinalInteger(0);
		FinalInteger completed = new FinalInteger(0);
		FinalInteger acompleted = new FinalInteger(0);
		ms = M.ms();
		int tchu = 0;

		for(ISelector i : selectors)
		{
			if(i.getType().equals(Chunk.class))
			{
				tchu += i.getPossibilities().size();
			}
		}

		source.sendResponseActing(Lang.getString("action.purge-chunks.purging") + F.f(tchu) + Lang.getString("action.purge-chunks.chunk") + ((tchu > 1 || tchu == 0) ? "s" : "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

		for(ISelector i : selectors)
		{
			if(i.getType().equals(Chunk.class))
			{
				total.add(i.getPossibilities().size());
				double mk = 2;
				for(Object j : i.getPossibilities())
				{
					if(i.can(j))
					{
						mk += 0.03;
						int dk = (int) mk;
						new Task("waiter-tr", 0, (int) dk) //$NON-NLS-1$
						{
							@Override
							public void run()
							{
								if((int) dk - 1 == ticks)
								{
									purge((Chunk) j, new Runnable()
									{
										@Override
										public void run()
										{
											if(!fail)
											{
												acompleted.add(1);
											}

											completed.add(1);
											String s = Info.ACTION_PURGE_CHUNKS_STATUS;
											setProgress((double) completed.get() / (double) total.get());
											s = s.replace("$c", F.f(completed.get())); //$NON-NLS-1$
											s = s.replace("$t", F.f(total.get())); //$NON-NLS-1$
											s = s.replace("$p", F.pc(getProgress(), 0)); //$NON-NLS-1$
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
												source.sendResponseSuccess(Lang.getString("action.purge-chunks.purged") + F.f(acompleted.get()) + Lang.getString("action.purge-chunks.chunk") + ((acompleted.get() > 1 || acompleted.get() == 0) ? "s" : "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
											}
										}
									}, source, selectors);

									cancel();
								}
							}
						};
					}
				}
			}
		}

		new Task("purger-monitor-callback", 30) //$NON-NLS-1$
		{
			@Override
			public void run()
			{
				if(M.ms() - ms > 1000 && getState().equals(ActionState.RUNNING))
				{
					cancel();
					completeAction();
					source.sendResponseSuccess(Lang.getString("action.purge-chunks.purged") + F.f(acompleted.get()) + Lang.getString("action.purge-chunks.chunk") + ((acompleted.get() > 1 || acompleted.get() == 0) ? "s" : "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				}
			}

		};
	}

	public void purge(Chunk chunk, Runnable cb, IActionSource source, ISelector... selectors)
	{
		try
		{
			fail = true;

			if(Gate.unloadChunk(chunk))
			{
				fail = false;
			}

			ms = M.ms();
			cb.run();
		}

		catch(Throwable e)
		{
			new S("action.purgechunk")
			{
				@Override
				public void run()
				{
					fail = true;

					if(Gate.unloadChunk(chunk))
					{
						fail = false;
					}

					ms = M.ms();
					cb.run();
				}
			};
		}
	}

	@Override
	public String getNode()
	{
		return "purge-chunks";
	}
}
