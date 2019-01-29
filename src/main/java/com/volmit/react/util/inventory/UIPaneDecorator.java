package com.volmit.react.util.inventory;

import org.bukkit.Material;

import com.volmit.react.util.C;
import com.volmit.react.util.MaterialBlock;

public class UIPaneDecorator extends UIStaticDecorator
{
	public UIPaneDecorator(C color)
	{
		super(new UIElement("c").setName(" ").setMaterial(new MaterialBlock(Material.STAINED_GLASS_PANE, color.getItemMeta())));
	}
}
