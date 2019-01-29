package com.volmit.react;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import com.volmit.react.api.Capability;
import com.volmit.react.api.Permissable;
import com.volmit.react.legacy.server.ReactServer;
import com.volmit.react.util.A;
import com.volmit.react.util.Control;
import com.volmit.react.util.Ex;
import com.volmit.react.util.HotloadManager;
import com.volmit.react.util.IController;
import com.volmit.react.util.P;
import com.volmit.react.util.ParallelPoolManager;
import com.volmit.react.util.Profiler;
import com.volmit.react.util.Protocol;
import com.volmit.react.util.S;
import com.volmit.react.util.TICK;
import com.volmit.react.util.Task;
import com.volmit.react.util.TaskLater;
import com.volmit.volume.lang.collections.GList;

public class ReactPlugin extends JavaPlugin
{
	public static ReactPlugin i;
	private GList<IController> controllers;
	private React react;
	private ParallelPoolManager pool;
	private HotloadManager mgr;
	private ReactServer srv;

	@SuppressWarnings("deprecation")
	@Override
	public void onEnable()
	{
		controllers = new GList<IController>();
		Surge.m();
		i = this;
		react = new React();
		React.instance = react;

		pool = new ParallelPoolManager(1)
		{
			@Override
			public long getNanoGate()
			{
				return 0;
			}
		};

		S.mgr = pool;
		Config.onRead(i);

		for(Field i : React.class.getFields())
		{
			if(i.isAnnotationPresent(Control.class))
			{
				try
				{
					IController controller = (IController) i.getType().getConstructor().newInstance();
					i.set(react, controller);
					controllers.add(controller);
				}

				catch(Throwable e)
				{
					e.printStackTrace();
				}
			}
		}

		for(IController i : controllers)
		{
			try
			{
				i.start();
			}

			catch(Throwable e)
			{
				e.printStackTrace();
			}
		}

		mgr = new HotloadManager();
		Runnable rl = new Runnable()
		{
			@Override
			public void run()
			{
				for(Player i : P.onlinePlayers())
				{
					if(Permissable.ACCESS.has(i) || i.isOp())
					{
						Gate.msgSuccess(i, "Injecting Configuration Changes");
					}
				}

				reload();
			}
		};

		mgr.track(new File(getDataFolder(), "config.yml"), rl);
		mgr.track(new File(getDataFolder(), "config-experimental.yml"), rl);

		new Task("controller-main.tick", 0)
		{
			@Override
			public void run()
			{
				TICK.tick++;
				doTick();
			}
		};

		if(Capability.PLACEHOLDERS.isCapable())
		{
			PlaceholderHandler h = new PlaceholderHandler();
			h.hook();

			new TaskLater("waiter", 5)
			{
				@Override
				public void run()
				{
					new A()
					{
						@Override
						public void run()
						{
							try
							{
								h.writeToFile();
							}

							catch(IOException e)
							{
								e.printStackTrace();
							}
						}
					};
				}
			};
		}

		if(Config.LEGACY_SERVER)
		{
			new TaskLater("waiter-srv", 12)
			{
				@Override
				public void run()
				{
					startRLServer();
				}
			};
		}

		System.out.println(Protocol.getProtocolVersion().getVersionString());
		if(Protocol.getProtocolVersion().equals(Protocol.R1_13))
		{
			Gate.safe = true;
			System.out.println("1.13 Detected, Safe mode activated.");
		}
	}

	private void doTick()
	{
		if(TICK.tick % 20 == 0)
		{
			try
			{
				mgr.onTick();
			}

			catch(Throwable e)
			{
				Ex.t(e);
			}
		}

		for(IController i : controllers.copy())
		{
			doTick(i);
		}

		try
		{
			pool.tickSyncQueue();
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}
	}

	private void doTick(IController i)
	{
		if(TICK.tick % (i.getInterval() < 1 ? 1 : i.getInterval()) != 0)
		{
			return;
		}

		if(i.isUrgent())
		{
			handle(i);
		}

		else
		{
			new S("tick-task-" + i.getClass().getSimpleName())
			{
				@Override
				public void run()
				{
					handle(i);
				}
			};
		}
	}

	private void handle(IController i)
	{
		try
		{
			Profiler p = new Profiler();
			p.begin();
			i.tick();
			p.end();
			i.setTime(p.getMilliseconds());
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}
	}

	public void startRLServer()
	{
		new TaskLater("", 12)
		{
			@Override
			public void run()
			{
				new A()
				{
					@Override
					public void run()
					{
						try
						{
							srv = new ReactServer(Config.LEGACY_SERVER_PORT);
							srv.start();
						}

						catch(IOException e)
						{
							System.out.println("COULD NOT START REACT REMOTE SERVER");
							e.printStackTrace();
						}
					}
				};
			}
		};
	}

	@SuppressWarnings("deprecation")
	public void stopRLServer()
	{
		new Thread("React Remote Killer")
		{
			@Override
			public void run()
			{
				if(srv != null)
				{
					if(srv.isAlive())
					{
						srv.interrupt();

						try
						{
							srv.join(1000);

							if(srv.isAlive())
							{
								try
								{
									srv.serverSocket.close();
								}

								catch(IOException e)
								{

								}

								try
								{
									srv.stop();
								}

								catch(Throwable e)
								{

								}

								try
								{
									srv.destroy();
								}

								catch(Throwable e)
								{

								}
							}
						}

						catch(InterruptedException e)
						{
							e.printStackTrace();
						}
					}
				}
			}
		}.start();
	}

	@Override
	public void onDisable()
	{
		for(IController i : controllers)
		{
			try
			{
				i.stop();
			}

			catch(Throwable e)
			{
				e.printStackTrace();
			}
		}

		mgr.untrackall();
		controllers.clear();
		pool.shutdown();
		stopRLServer();
	}

	public static void reload()
	{
		React.instance().stopRLServer();
		new TaskLater("", 5)
		{
			@Override
			public void run()
			{
				i.onDisable();
				Bukkit.getScheduler().cancelTasks(i);
				HandlerList.unregisterAll(i);
				i.onEnable();
			}
		};
	}

	public static ReactPlugin getI()
	{
		return i;
	}

	public GList<IController> getControllers()
	{
		return controllers;
	}

	public React getReact()
	{
		return react;
	}

	public ParallelPoolManager getPool()
	{
		return pool;
	}

	public HotloadManager getMgr()
	{
		return mgr;
	}

	public ReactServer getSrv()
	{
		return srv;
	}

	public int startTask(int delay, Runnable r)
	{
		return Bukkit.getScheduler().scheduleSyncDelayedTask(this, r, delay);
	}

	public int startRepeatingTask(int delay, int interval, Runnable r)
	{
		return Bukkit.getScheduler().scheduleSyncRepeatingTask(this, r, delay, interval);
	}

	public void stopTask(int id)
	{
		Bukkit.getScheduler().cancelTask(id);
	}
}
