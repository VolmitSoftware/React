package primal.bukkit.world;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import primal.lang.collection.GList;

/**
 * ItemStack & Inventory utilities
 *
 * @author cyberpwn
 */
public class Inventories
{
	/**
	 * Does the inventory have any space (empty slots)
	 *
	 * @param i
	 *            the inventory
	 * @return true if it has at least one slot empty
	 */
	public static boolean hasSpace(Inventory i)
	{
		return i.firstEmpty() != -1;
	}

	/**
	 * Does the inventory have a given amount of empty space (or more)
	 *
	 * @param i
	 *            the inventory
	 * @param slots
	 *            the slots needed empty
	 * @return true if it has more than or enough empty slots
	 */
	public static boolean hasSpace(Inventory i, int slots)
	{
		return getEmptySlots(i) >= slots;
	}

	/**
	 * Get the ACTUAL contents in this inventory. Meaning no elements in the list
	 * are null, or just plain air
	 *
	 * @param i
	 *            the inventory
	 * @return the ACTUAL contents
	 */
	public static GList<ItemStack> getActualContents(Inventory i)
	{
		GList<ItemStack> actualItems = new GList<ItemStack>();

		for(ItemStack j : i.getContents())
		{
			if(Items.is(j))
			{
				actualItems.add(j);
			}
		}

		return actualItems;
	}

	/**
	 * Does the inventory have space for the given item
	 *
	 * @param i
	 *            the inventory
	 * @param item
	 *            the item
	 * @return returns true if either there is enough empty slots to fill it (amt /
	 *         maxStackSize) OR the item can be merged with an existing item, else
	 *         false.
	 */
	public static boolean hasSpace(Inventory i, ItemStack item)
	{
		if(hasSpace(i, item.getAmount() / item.getMaxStackSize()))
		{
			return true;
		}

		else
		{
			for(ItemStack j : getActualContents(i))
			{
				if(Items.isMergable(j, item))
				{
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Get the count of empty slots (or air slots)
	 *
	 * @param i
	 *            the inventory
	 * @return the number of empty slots
	 */
	public static int getEmptySlots(Inventory ix)
	{
		int x = 0;

		for(ItemStack i : ix.getContents())
		{
			if(i == null || i.getType().equals(Material.AIR))
			{
				x++;
			}
		}

		return x;
	}
}