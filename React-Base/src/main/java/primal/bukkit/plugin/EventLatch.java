package primal.bukkit.plugin;

import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

public class EventLatch<T extends Event> implements Listener
{
	private Class<? extends T> t;
	private EventPriority priority;
	private boolean ignoreCancelled;
	private Function<T, Boolean> filter;
	private Consumer<T> c;

	public EventLatch(Class<? extends T> t)
	{
		this.t = t;
		priority = EventPriority.NORMAL;
		ignoreCancelled = true;
		filter = (x) -> true;
	}

	public EventLatch<T> priority(EventPriority c)
	{
		this.priority = c;
		return this;
	}

	public EventLatch<T> ignoreCancelled(boolean c)
	{
		this.ignoreCancelled = c;
		return this;
	}

	public EventLatch<T> consumer(Consumer<T> c)
	{
		this.c = c;
		return this;
	}

	public EventLatch<T> filter(Function<T, Boolean> filter)
	{
		this.filter = filter;
		return this;
	}

	public void bind()
	{
		Bukkit.getPluginManager().registerEvent(t, this, priority, new EventExecutor()
		{
			@SuppressWarnings("unchecked")
			@Override
			public void execute(Listener arg0, Event arg1) throws EventException
			{
				if(filter.apply((T) arg1))
				{
					unbind();
					c.accept((T) arg1);
				}
			}
		}, PrimalPlugin.instance, ignoreCancelled);
	}

	public void unbind()
	{
		HandlerList.unregisterAll(this);
	}
}
