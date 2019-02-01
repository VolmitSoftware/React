package com.volmit.react.action;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.volmit.react.Gate;
import com.volmit.react.Info;
import com.volmit.react.api.Action;
import com.volmit.react.api.ActionState;
import com.volmit.react.api.ActionType;
import com.volmit.react.api.IActionSource;
import com.volmit.react.api.ISelector;
import com.volmit.react.api.SelectorPosition;
import com.volmit.react.api.SelectorTime;
import com.volmit.react.util.AccessCallback;
import com.volmit.react.util.F;
import com.volmit.react.util.FinalInteger;
import com.volmit.react.util.M;
import com.volmit.react.util.Task;

public class ActionThrottlePhysics extends Action
{
	private long ms;
	private int lcd;
	private boolean fail;

	public ActionThrottlePhysics()
	{
		super(ActionType.THROTTLE_PHYSICS);
		fail = false;
		setNodes(Info.ACTION_THROTTLE_PHYSICS_TAGS);

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

		setDefaultSelector(Long.class, new AccessCallback<ISelector>()
		{
			@Override
			public ISelector get()
			{
				SelectorTime sel = new SelectorTime();
				sel.set((long) 2000);

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
		long timeFor = 3000;

		for(ISelector i : selectors)
		{
			if(i.getType().equals(Chunk.class))
			{
				tchu += i.getPossibilities().size();
			}

			if(i.getType().equals(Long.class))
			{
				timeFor = ((SelectorTime) i).get();
			}
		}

		long tf = timeFor;

		source.sendResponseActing("Throttling Physics in " + F.f(tchu) + " chunk" + ((tchu > 1 || tchu == 0) ? "s" : "") + " for " + F.time(timeFor, 1)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

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
									throttle(tf, (Chunk) j, new Runnable()
									{
										@Override
										public void run()
										{
											if(!fail)
											{
												acompleted.add(1);
											}

											completed.add(1);
											setProgress((double) completed.get() / (double) total.get());
											setStatus(F.pc(getProgress()));
											ms = M.ms();
											totalCulled.add(lcd);

											if(lcd > 0)
											{
												totalChunked.add(1);
											}

											if(completed.get() == total.get())
											{
												completeAction();
												source.sendResponseSuccess("Throttled Physics in " + F.f(acompleted.get()) + " chunk" + ((acompleted.get() > 1 || acompleted.get() == 0) ? "s" : "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
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

		new Task("throttle-monitor-callback", 30) //$NON-NLS-1$
		{
			@Override
			public void run()
			{
				if(M.ms() - ms > 1000 && getState().equals(ActionState.RUNNING))
				{
					cancel();
					completeAction();
					source.sendResponseSuccess("Throttled Physics in " + F.f(acompleted.get()) + " chunk" + ((acompleted.get() > 1 || acompleted.get() == 0) ? "s" : "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				}
			}

		};
	}

	public void throttle(long time, Chunk chunk, Runnable cb, IActionSource source, ISelector... selectors)
	{
		try
		{
			fail = true;

			if(Gate.throttleChunk(chunk, 15, time))
			{
				fail = false;
			}

			ms = M.ms();
			cb.run();
		}

		catch(Throwable e)
		{

		}
	}

	@Override
	public String getNode()
	{
		return "throttle-physics";
	}

	@Override
	public ItemStack getIcon()
	{
		return new ItemStack(Material.FIREWORK);
	}
}
