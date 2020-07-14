package primal.bukkit.inventory;

import org.bukkit.Material;

import primal.bukkit.world.MaterialBlock;
import primal.util.text.C;

public class UIPaneDecorator extends UIStaticDecorator
{
	public UIPaneDecorator(C color)
	{
		super(new UIElement("c").setName(" ").setMaterial(new MaterialBlock(Material.STAINED_GLASS_PANE, color.getItemMeta())));
	}
}
