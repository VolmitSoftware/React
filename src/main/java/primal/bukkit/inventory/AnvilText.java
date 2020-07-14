package primal.bukkit.inventory;

import java.util.function.BiFunction;

import org.bukkit.entity.Player;

import primal.bukkit.plugin.PrimalPlugin;

public class AnvilText
{
	@SuppressWarnings("deprecation")
	public static void getText(Player p, String def, RString s)
	{
		try
		{
			new AnvilGUI(PrimalPlugin.instance, p, def, new BiFunction<Player, String, String>()
			{
				@Override
				public String apply(Player t, String u)
				{
					s.onComplete(u);
					t.closeInventory();

					return "";
				}
			});
		}

		catch(Exception e)
		{

		}
	}
}