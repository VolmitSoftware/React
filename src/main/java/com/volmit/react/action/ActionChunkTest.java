package com.volmit.react.action;

import org.bukkit.Chunk;

import com.volmit.react.Gate;
import com.volmit.react.Info;
import com.volmit.react.api.Action;
import com.volmit.react.api.ActionType;
import com.volmit.react.api.IActionSource;
import com.volmit.react.api.ISelector;
import com.volmit.react.api.SelectorPosition;
import com.volmit.react.api.SelectorTime;
import com.volmit.react.util.AccessCallback;
import com.volmit.react.util.F;

public class ActionChunkTest extends Action
{
	public ActionChunkTest()
	{
		super(ActionType.CHUNK_TEST);
		setNodes(Info.ACTION_CHUNK_TEST_TAGS);

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
				sel.set((long) 1000);

				return sel;
			}
		});
	}

	@Override
	public void enact(IActionSource source, ISelector... selectors)
	{
		long timeFor = 10000;
		for(ISelector i : selectors)
		{
			if(i.getType().equals(Long.class))
			{
				timeFor = ((SelectorTime) i).get();
			}
		}

		int tchu = 0;

		for(ISelector i : selectors)
		{
			if(i.getType().equals(Chunk.class))
			{
				tchu += i.getPossibilities().size();
			}
		}

		source.sendResponseActing("Benchmarking " + F.f(tchu) + " Chunk" + (tchu == 1 ? "" : "s") + " for " + F.time(timeFor, 1)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

		for(ISelector i : selectors)
		{
			if(i.getType().equals(Chunk.class))
			{
				Gate.benchmark(i.getPossibilities(), source, (int) (timeFor / 50), new Runnable()
				{
					@Override
					public void run()
					{
						completeAction();
					}
				});
			}
		}
	}

	@Override
	public String getNode()
	{
		return "chunk-test";
	}
}
