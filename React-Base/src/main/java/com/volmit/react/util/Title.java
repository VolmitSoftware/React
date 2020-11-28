package com.volmit.react.util;

import org.bukkit.entity.Player;

public class Title
{
	private String title;
	private String subTitle;
	private String action;
	private Integer fadeIn;
	private Integer fadeOut;
	private Integer stayTime;

	public Title()
	{
		fadeIn = 0;
		fadeOut = 0;
		stayTime = 5;
	}

	public Title(String title, String subTitle, String action, Integer fadeIn, Integer fadeOut, Integer stayTime)
	{
		this.title = title;
		this.subTitle = subTitle;
		this.action = action;
		this.fadeIn = fadeIn;
		this.fadeOut = fadeOut;
		this.stayTime = stayTime;
	}

	public Title(String title, String subTitle, Integer fadeIn, Integer fadeOut, Integer stayTime)
	{
		this.title = title;
		this.subTitle = subTitle;
		this.fadeIn = fadeIn;
		this.fadeOut = fadeOut;
		this.stayTime = stayTime;
	}

	public Title(String action, Integer fadeIn, Integer fadeOut, Integer stayTime)
	{
		this.action = action;
		this.fadeIn = fadeIn;
		this.fadeOut = fadeOut;
		this.stayTime = stayTime;
	}

	public void send(Player p)
	{
		try
		{
			PacketUtil.sendTitle(p, fadeIn, stayTime, fadeOut, title, subTitle);
			PacketUtil.sendActionBar(p, action);
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}
	}

	public String getTitle()
	{
		return title;
	}

	public void setTitle(String title)
	{
		this.title = title;
	}

	public String getSubTitle()
	{
		return subTitle;
	}

	public void setSubTitle(String subTitle)
	{
		this.subTitle = subTitle;
	}

	public String getAction()
	{
		return action;
	}

	public void setAction(String action)
	{
		this.action = action;
	}

	public Integer getFadeIn()
	{
		return fadeIn;
	}

	public void setFadeIn(Integer fadeIn)
	{
		this.fadeIn = fadeIn;
	}

	public Integer getFadeOut()
	{
		return fadeOut;
	}

	public void setFadeOut(Integer fadeOut)
	{
		this.fadeOut = fadeOut;
	}

	public Integer getStayTime()
	{
		return stayTime;
	}

	public void setStayTime(Integer stayTime)
	{
		this.stayTime = stayTime;
	}
}
