package com.volmit.react.api;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;

import com.volmit.react.Info;
import com.volmit.react.React;
import com.volmit.react.Surge;
import com.volmit.react.util.ConfigurationDataInput;
import com.volmit.react.util.ConfigurationDataOutput;
import com.volmit.react.util.Ex;
import com.volmit.react.util.IConfigurable;
import com.volmit.react.util.KeyPointer;
import com.volmit.react.util.KeyStore;
import com.volmit.volume.lang.collections.GMap;

public class ReactPlayer implements IConfigurable
{
	@KeyStore
	public static GMap<Integer, String> keystore;

	static
	{
		keystore = new GMap<Integer, String>();
		keystore.put(-175, Info.STATE_MONITORING_ENABLED);
		keystore.put(-172, Info.STATE_ACTIONLOGGING_ENABLED);
		keystore.put(234, Info.STATE_MAPPING_ENABLED);
		keystore.put(235, Info.STATE_MAPPING_SCROLLPOS);
		keystore.put(844, Info.STATE_MAPPING_ARGS);
		keystore.put(-885, Info.STATE_MONITORING_TAB);
		keystore.put(343, Info.STATE_SOUND_PLAYS);
		keystore.put(765, Info.STATE_MONITORING_POSTED);
		keystore.put(-112, Info.STATE_MONITORING_LASTTAB);
		keystore.put(-115, Info.STATE_MONITORING_SWT);
		keystore.put(-694, Info.STATE_MONITORING_SWITCHNOTIFICATION);
		keystore.put(492, Info.STATE_PLAYER_HOTBAR);
		keystore.put(117, Info.STATE_PLAYER_SHIFT);
		keystore.put(-592, Info.STATE_PLAYER_SCROLL);
		keystore.put(841, Info.STATE_PLAYER_HEIGHT_CURRENT);
		keystore.put(-413, Info.STATE_PLAYER_HEIGHT_CHANGING);
		keystore.put(164, Info.STATE_GLASSES_ENABLED);
		keystore.put(-1783, Info.STATE_MONITORING_HIGH);
		keystore.put(-845, Info.STATE_CHANNELS);
	}

	@KeyPointer(-175)
	public boolean monitoring = false;

	@KeyPointer(-115)
	public int lastSwt = 0;

	@KeyPointer(-1783)
	public boolean highMonitor = false;

	@KeyPointer(-172)
	public boolean actionlogging = false;

	@KeyPointer(164)
	public boolean glasses = false;

	@KeyPointer(234)
	public boolean mapping = false;

	@KeyPointer(235)
	public int mapScroll = 0;

	@KeyPointer(-885)
	public int monitorSelection = -1;

	@KeyPointer(343)
	public int plays = 0;

	@KeyPointer(765)
	public boolean monitorPosted = false;

	@KeyPointer(-112)
	public int monitorLastSelection = 0;

	@KeyPointer(-694)
	public int switchNotification = 0;

	@KeyPointer(492)
	public int hotbarSlot = 0;

	@KeyPointer(117)
	public boolean shift = false;

	@KeyPointer(-592)
	public int scroll = 0;

	@KeyPointer(841)
	public double lastHeight = 0;

	@KeyPointer(844)
	public String pargs = "";

	@KeyPointer(-845)
	public List<String> channels = new ArrayList<String>();

	@KeyPointer(-413)
	public boolean heightMovement = false;

	private Player p;

	public ReactPlayer(Player p)
	{
		this.p = p;
		load();
	}

	public boolean hasChannel(String s)
	{
		return channels.contains(s);
	}

	public void save()
	{
		try
		{
			new ConfigurationDataOutput().write(this, new File(Surge.folder(Info.CORE_CACHE), p.getUniqueId().toString() + Info.CORE_DOTYML));
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}
	}

	public void load()
	{
		try
		{
			new ConfigurationDataInput().read(this, new File(Surge.folder(Info.CORE_CACHE), p.getUniqueId().toString() + Info.CORE_DOTYML));
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}
	}

	public boolean isMonitoring()
	{
		return monitoring;
	}

	public boolean isMapping()
	{
		return mapping;
	}

	public Player getP()
	{
		return p;
	}

	public void addChannel(String c)
	{
		channels.add(c);
		React.instance.playerController.requestSave(getP(), false);
	}

	public void removeChannel(String c)
	{
		while(channels.contains(c))
		{
			channels.remove(c);
		}

		React.instance.playerController.requestSave(getP(), false);
	}

	public void removeAllChannels()
	{
		channels.clear();
		React.instance.playerController.requestSave(getP(), false);
	}

	public void setMonitoring(boolean monitoring)
	{
		React.instance.playerController.requestSave(getP(), false);
		this.monitoring = monitoring;
	}

	public void setMapping(boolean mapping)
	{
		React.instance.playerController.requestSave(getP(), false);
		this.mapping = mapping;
	}

	public int getMonitorSelection()
	{
		return monitorSelection;
	}

	public void setMonitorSelection(int monitorSelection)
	{
		React.instance.playerController.requestSave(getP(), false);
		this.monitorSelection = monitorSelection;
	}

	public int getHotbarSlot()
	{
		return hotbarSlot;
	}

	public void setHotbarSlot(int hotbarSlot)
	{
		React.instance.playerController.requestSave(getP(), false);
		this.hotbarSlot = hotbarSlot;
	}

	public boolean isShifting()
	{
		return shift;
	}

	public void setShifting(boolean shift)
	{
		React.instance.playerController.requestSave(getP(), false);
		this.shift = shift;
	}

	public boolean isShift()
	{
		return shift;
	}

	public void setShift(boolean shift)
	{
		React.instance.playerController.requestSave(getP(), false);
		this.shift = shift;
	}

	public int getScroll()
	{
		return scroll;
	}

	public void setScroll(int scroll)
	{
		React.instance.playerController.requestSave(getP(), false);
		this.scroll = scroll;
	}

	public int getMonitorLastSelection()
	{
		return monitorLastSelection;
	}

	public void setMonitorLastSelection(int monitorLastSelection)
	{
		React.instance.playerController.requestSave(getP(), false);
		this.monitorLastSelection = monitorLastSelection;
	}

	public void setP(Player p)
	{
		this.p = p;
	}

	public double getLastHeight()
	{
		return lastHeight;
	}

	public void setLastHeight(double lastHeight)
	{
		React.instance.playerController.requestSave(getP(), false);
		this.lastHeight = lastHeight;
	}

	public boolean isHeightMovement()
	{
		return heightMovement;
	}

	public void setHeightMovement(boolean heightMovement)
	{
		React.instance.playerController.requestSave(getP(), false);
		this.heightMovement = heightMovement;
	}

	public int getSwitchNotification()
	{
		return switchNotification;
	}

	public void setSwitchNotification(int switchNotification)
	{
		React.instance.playerController.requestSave(getP(), false);
		this.switchNotification = switchNotification;
	}

	public boolean getMonitorPosted()
	{
		return monitorPosted;
	}

	public void setMonitorPosted(boolean monitorPosted)
	{
		React.instance.playerController.requestSave(getP(), false);
		this.monitorPosted = monitorPosted;
	}

	public int getPlays()
	{
		return plays;
	}

	public void setPlays(int plays)
	{
		React.instance.playerController.requestSave(getP(), false);
		this.plays = plays;
	}

	public boolean isGlasses()
	{
		return glasses;
	}

	public void setGlasses(boolean glasses)
	{
		React.instance.playerController.requestSave(getP(), false);
		this.glasses = glasses;
	}

	public static GMap<Integer, String> getKeystore()
	{
		return keystore;
	}

	public static void setKeystore(GMap<Integer, String> keystore)
	{
		ReactPlayer.keystore = keystore;
	}

	public int getLastSwt()
	{
		return lastSwt;
	}

	public void setLastSwt(int lastSwt)
	{
		this.lastSwt = lastSwt;
	}

	public boolean isHighMonitor()
	{
		return highMonitor;
	}

	public void setHighMonitor(boolean highMonitor)
	{
		this.highMonitor = highMonitor;
	}

	public List<String> getChannels()
	{
		return channels;
	}

	public void setChannels(List<String> channels)
	{
		this.channels = channels;
	}

	public boolean isActionlogging()
	{
		return actionlogging;
	}

	public void setActionlogging(boolean actionlogging)
	{
		React.instance.playerController.requestSave(getP(), false);
		this.actionlogging = actionlogging;
	}
}
