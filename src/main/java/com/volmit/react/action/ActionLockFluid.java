package com.volmit.react.action;

import org.bukkit.Chunk;

import com.volmit.react.Info;
import com.volmit.react.Lang;
import com.volmit.react.React;
import com.volmit.react.api.Action;
import com.volmit.react.api.ActionType;
import com.volmit.react.api.IActionSource;
import com.volmit.react.api.ISelector;
import com.volmit.react.api.SelectorPosition;
import com.volmit.react.api.SelectorTime;
import com.volmit.react.util.AccessCallback;
import com.volmit.react.util.F;
import com.volmit.react.util.FinalInteger;

public class ActionLockFluid extends Action
{
	private int lcd;

	public ActionLockFluid()
	{
		super(ActionType.LOCK_FLUID);

		setNodes(Info.ACTION_LOCK_FLUID_TAGS);

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
				sel.set((long) 10000);

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
		long timeFor = 10000;

		for(ISelector i : selectors)
		{
			if(i.getType().equals(Long.class))
			{
				timeFor = ((SelectorTime) i).get();
			}
		}

		for(ISelector i : selectors)
		{
			if(i.getType().equals(Chunk.class))
			{
				total.add(i.getPossibilities().size());

				for(Object j : i.getPossibilities())
				{
					if(i.can(j))
					{
						React.instance.fluidController.freeze(((Chunk) j), timeFor);
						completed.add(1);
						String s = Info.ACTION_LOCK_FLUID_STATUS;
						setProgress((double) completed.get() / (double) total.get());
						s = s.replace("$c", F.f(completed.get())); //$NON-NLS-1$
						s = s.replace("$t", F.f(total.get())); //$NON-NLS-1$
						s = s.replace("$p", F.pc(getProgress(), 0)); //$NON-NLS-1$
						setStatus(s);
						totalCulled.add(lcd);

						if(lcd > 0)
						{
							totalChunked.add(1);
						}
					}
				}
			}
		}

		completeAction();
		source.sendResponseSuccess(Lang.getString("action.lock-fluid.locked-in") + F.f(completed.get()) + Lang.getString("action.lock-fluid.chunk") + ((completed.get() > 1 || completed.get() == 0) ? "s" : "") + Lang.getString("action.lock-fluid.for") + F.time(timeFor, 1)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
	}

	@Override
	public String getNode()
	{
		return "lock-fluid";
	}
}
