package com.volmit.react.controller;

import org.bukkit.entity.Player;

import com.volmit.react.Config;
import com.volmit.react.Gate;
import com.volmit.react.Info;
import com.volmit.react.React;
import com.volmit.react.Surge;
import com.volmit.react.api.Note;
import com.volmit.react.api.Notification;
import com.volmit.react.api.Permissable;
import com.volmit.react.api.ReactPlayer;
import com.volmit.react.util.C;
import com.volmit.react.util.Callback;
import com.volmit.react.util.Controller;
import com.volmit.react.util.D;
import com.volmit.react.util.Ex;
import com.volmit.react.util.F;
import com.volmit.react.util.JSONObject;
import com.volmit.react.util.TXT;
import com.volmit.volume.lang.collections.GList;

public class MessageController extends Controller
{
	private GList<Notification> notes;

	@Override
	public void dump(JSONObject object)
	{
		object.put("queue", notes.size());
	}

	public void queue(Notification notification)
	{
		notes.add(notification);
	}

	@Override
	public void start()
	{
		notes = new GList<Notification>();
		Surge.register(this);
		D.scall = new Callback<String>()
		{
			@Override
			public void run(String s)
			{
				notes.add(new Notification(Note.VERBOSE, s));
			}
		};
	}

	@Override
	public void stop()
	{
		D.scall = null;
		Surge.unregister(this);
	}

	@Override
	public void tick()
	{
		for(Notification i : notes.copy())
		{
			process(i);
		}

		notes.clear();
	}

	public void subscribe(Player p, Note note)
	{
		if(!Permissable.MONITOR.has(p))
		{
			Gate.msgError(p, "No Permission");
			return;
		}

		if(!React.instance.playerController.getPlayer(p).hasChannel(note.toString()))
		{
			Gate.msg(p, "Channel " + note.toString().toLowerCase() + " subscribed.");
			React.instance.playerController.getPlayer(p).addChannel(note.toString());
		}

		else
		{
			Gate.msgError(p, "Channel " + note.toString().toLowerCase() + " already subscribed.");
		}
	}

	public void unsubscribe(Player p, Note note)
	{
		if(!Permissable.MONITOR.has(p))
		{
			Gate.msgError(p, "No Permission");
			return;
		}

		if(React.instance.playerController.getPlayer(p).hasChannel(note.toString()))
		{
			Gate.msg(p, "Channel " + note.toString().toLowerCase() + " unsubscribed.");
			React.instance.playerController.getPlayer(p).removeChannel(note.toString());
		}

		else
		{
			Gate.msgError(p, "Channel " + note.toString().toLowerCase() + " not subscribed.");
		}
	}

	public GList<Note> getSubscriptions(Player p)
	{
		if(!Permissable.MONITOR.has(p))
		{
			return new GList<Note>();
		}

		GList<Note> notes = new GList<Note>();

		for(String i : React.instance.playerController.getPlayer(p).channels)
		{
			try
			{
				notes.add(Note.valueOf(i));
			}

			catch(Throwable e)
			{
				Ex.t(e);
			}
		}

		return notes;
	}

	public boolean isSubscribed(Player p, Note note)
	{
		if(!Permissable.MONITOR.has(p))
		{
			return false;
		}

		return getSubscriptions(p).contains(note);
	}

	public void unsubscribeAll(Player p)
	{
		if(!Permissable.MONITOR.has(p))
		{
			Gate.msgError(p, "No Permission");
			return;
		}

		for(Note i : getSubscriptions(p))
		{
			unsubscribe(p, i);
		}
	}

	public void subscribeAll(Player p)
	{
		if(!Permissable.MONITOR.has(p))
		{
			Gate.msgError(p, "No Permission");
			return;
		}

		for(Note i : Note.values())
		{
			if(!isSubscribed(p, i))
			{
				subscribe(p, i);
			}
		}
	}

	private void process(Notification n)
	{
		for(ReactPlayer i : React.instance.playerController.getPlayers())
		{
			if(i.hasChannel(n.getType().toString()))
			{
				Gate.msg(i, n);
			}
		}

		if(Config.getSelectedChannels().contains(n.getType()) && Config.VERBOSE_CHANNEL_ENABLE)
		{
			String s = TXT.makeTag(C.AQUA, C.DARK_GRAY, C.GRAY, Info.CORE_NAME + " - " + C.WHITE + F.capitalizeWords(n.getType().toString().toLowerCase())) + n.getMessage();
			Gate.console(s);
		}
	}

	@Override
	public int getInterval()
	{
		return 19;
	}

	@Override
	public boolean isUrgent()
	{
		return false;
	}
}
