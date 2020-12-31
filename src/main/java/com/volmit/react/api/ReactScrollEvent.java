package com.volmit.react.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

public class ReactScrollEvent extends ReactPlayerEvent implements Cancellable
{
	private ScrollDirection direction;
	private boolean cancellled;
	private int amount;

	public ReactScrollEvent(Player player, ScrollDirection direction, int amount)
	{
		super(player);
		this.direction = direction;
		cancellled = false;
		this.amount = amount;
	}

	public ScrollDirection getDirection()
	{
		return direction;
	}

	@Override
	public boolean isCancelled()
	{
		return cancellled;
	}

	@Override
	public void setCancelled(boolean cancellled)
	{
		this.cancellled = cancellled;
	}

	public boolean isCancellled()
	{
		return cancellled;
	}

	public int getAmount()
	{
		return amount;
	}
}
