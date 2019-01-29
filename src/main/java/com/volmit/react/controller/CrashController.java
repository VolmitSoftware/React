package com.volmit.react.controller;

import java.io.File;
import java.io.PrintWriter;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.volmit.react.Config;
import com.volmit.react.Gate;
import com.volmit.react.ReactPlugin;
import com.volmit.react.Surge;
import com.volmit.react.api.Flavor;
import com.volmit.react.util.C;
import com.volmit.react.util.CPS;
import com.volmit.react.util.ColoredString;
import com.volmit.react.util.Controller;
import com.volmit.react.util.JSONObject;
import com.volmit.react.util.M;
import com.volmit.react.util.RTEX;
import com.volmit.react.util.RTX;
import com.volmit.react.util.S;
import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GMap;
import com.volmit.volume.lang.format.F;

public class CrashController extends Controller
{
	public static long lastTick = -1;
	public static boolean online = false;
	public static CrashController inst = null;
	public static GMap<String, Integer> nag;

	@Override
	public void dump(JSONObject object)
	{
		object.put("last-tick", lastTick);
		object.put("online", online);
	}

	@Override
	public void start()
	{
		nag = new GMap<String, Integer>();
		Surge.register(this);
		lastTick = M.ms();
	}

	@Override
	public void stop()
	{
		Surge.unregister(this);
	}

	@Override
	public void tick()
	{
		if(Flavor.getHostFlavor().equals(Flavor.PAPER_SPIGOT))
		{
			return;
		}

		if(!Config.TRACK_SERVER_SPIKES)
		{
			return;
		}

		lastTick = M.ms();

		if(!online && !Bukkit.getOnlinePlayers().isEmpty())
		{
			online = true;
			inst = this;
			System.out.println("Plugin Watchdog thread started.");
		}

		for(String i : nag.k())
		{
			if(nag.get(i) < 20)
			{
				nag.remove(i);
			}

			nag.put(i, nag.get(i) - 20);
		}
	}

	public void run()
	{
		if(Flavor.getHostFlavor().equals(Flavor.PAPER_SPIGOT))
		{
			return;
		}

		if(!Config.TRACK_SERVER_SPIKES)
		{
			return;
		}

		if(Thread.interrupted())
		{
			return;
		}

		boolean spiked = false;

		if(M.ms() - lastTick > Config.TIME_SENS && !spiked)
		{
			boolean gc = false;
			spiked = true;
			GMap<Plugin, String> pxv = new GMap<Plugin, String>();
			StackTraceElement[] stt = Surge.getServerThread().getStackTrace();

			for(StackTraceElement i : stt)
			{
				for(Plugin k : CPS.identify(i.getClassName()))
				{
					if(pxv.containsKey(k))
					{
						continue;
					}

					if(nag.containsKey(k.getName()))
					{
						continue;
					}

					nag.put(k.getName(), Config.TIME_NAG);

					pxv.put(k, CPS.format(i));
				}
			}

			if(SampleController.lastGC > lastTick)
			{
				gc = true;
			}

			RTX rt = new RTX();
			rt.addText("[", Gate.darkColor);
			rt.addText("React", Gate.themeColor);
			rt.addText(" - ", Gate.darkColor);
			rt.addText("Alert", C.RED);
			rt.addText("]", Gate.darkColor);
			rt.addText(": ", Gate.textColor);
			rt.addText("Server Lock ", C.GRAY);
			rt.addText("-> ", C.WHITE);

			if(gc)
			{
				return;
			}

			else if(!pxv.isEmpty())
			{
				for(Plugin pi : pxv.k())
				{
					RTEX rtx = new RTEX();
					rtx.getExtras().add(new ColoredString(C.GRAY, pi.getName() + " may be responsible for freezing the server.\n\n"));
					int max = 11;

					if(Config.SAVE_SERVER_SPIKES)
					{
						try
						{
							String n = pi.getName() + "-" + F.stamp(M.ms()) + "-" + M.ms();
							File f = new File(ReactPlugin.i.getDataFolder(), "spikes");
							f.mkdirs();
							File ff = new File(f, n + ".txt");
							PrintWriter pw = new PrintWriter(ff);

							for(StackTraceElement i : stt)
							{
								pw.println(i.getClassName() + "." + i.getMethodName() + "(" + i.getLineNumber() + ")");
							}

							pw.close();
						}

						catch(Exception e)
						{
							e.printStackTrace();
						}
					}

					for(StackTraceElement i : stt)
					{
						if(pxv.get(pi).equals(CPS.format(i)))
						{
							rtx.getExtras().add(new ColoredString(C.YELLOW, trim(i.getClassName()) + "."));
							rtx.getExtras().add(new ColoredString(C.GOLD, i.getMethodName()));
							rtx.getExtras().add(new ColoredString(C.GRAY, "("));
							rtx.getExtras().add(new ColoredString(C.RED, i.getLineNumber() + ""));
							rtx.getExtras().add(new ColoredString(C.GRAY, ")\n"));
						}

						else
						{
							rtx.getExtras().add(new ColoredString(C.GRAY, trim(i.getClassName()) + "."));
							rtx.getExtras().add(new ColoredString(C.WHITE, i.getMethodName()));
							rtx.getExtras().add(new ColoredString(C.GRAY, "("));
							rtx.getExtras().add(new ColoredString(C.RED, i.getLineNumber() + ""));
							rtx.getExtras().add(new ColoredString(C.GRAY, ")\n"));
						}

						max--;

						if(max <= 0)
						{
							break;
						}
					}

					rt.addTextHover("[" + pi.getName() + "] ", rtx, C.RED);
				}
			}

			else
			{
				return;
			}

			for(CommandSender i : Gate.broadcastReactUsers())
			{
				if(i instanceof Player)
				{
					try
					{
						rt.tellRawTo((Player) i);
					}

					catch(Throwable e)
					{
						new S("tr-fix")
						{
							@Override
							public void run()
							{
								rt.tellRawTo((Player) i);
							}
						};
					}
				}
			}
		}

		if(M.ms() - lastTick < Config.TIME_SENS && spiked)
		{
			for(CommandSender i : Gate.broadcastReactUsers())
			{
				Gate.msgSuccess(i, "The server has recovered from a lock!");
			}

			System.out.println("[React]: The server has recovered and resumed ticking.");
			spiked = false;
		}
	}

	public String trim(String cls)
	{
		if(Config.FULL_PACKAGES)
		{
			return cls;
		}

		if(!cls.contains("."))
		{
			return cls;
		}

		String m = "";
		GList<String> v = new GList<String>(cls.split("\\."));
		String l = v.popLast();

		for(String i : v)
		{
			m += i.charAt(0) + ".";
		}

		m += l;

		return m;
	}

	public String fcf(String className)
	{
		return className.split("\\.")[className.split("\\.").length - 1];
	}

	@Override
	public int getInterval()
	{
		return 20;
	}

	@Override
	public boolean isUrgent()
	{
		return true;
	}
}
