package primal.bukkit.world;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import primal.lang.collection.GList;

/**
 * Itemstack utilities
 *
 * @author cyberpwn
 */
public class Items
{
	/**
	 * Is the item an item (not null or air)
	 *
	 * @param is
	 *            the item
	 * @return true if it is
	 */
	public static boolean is(ItemStack is)
	{
		return is != null && !is.getType().equals(Material.AIR);
	}

	/**
	 * Is the item a certain material
	 *
	 * @param is
	 *            the item
	 * @param material
	 *            the material
	 * @return true if it is
	 */
	public static boolean is(ItemStack is, Material material)
	{
		return is(is) && is.getType().equals(material);
	}

	/**
	 * Is the item a certain material and metadata
	 *
	 * @param is
	 *            the item
	 * @param mb
	 *            the materialblock
	 * @return true if it is
	 */
	@SuppressWarnings("deprecation")
	public static boolean is(ItemStack is, MaterialBlock mb)
	{
		return is(is, mb.getMaterial()) && is.getData().getData() == mb.getData();
	}

	/**
	 * Is the item a given material and data
	 *
	 * @param is
	 *            the item
	 * @param material
	 *            the material
	 * @param data
	 *            the data
	 * @return true if it is
	 */
	public static boolean is(ItemStack is, Material material, byte data)
	{
		return is(is, new MaterialBlock(material, data));
	}

	/**
	 * Is the item a given material and data
	 *
	 * @param is
	 *            the item
	 * @param material
	 *            the material
	 * @param data
	 *            the data
	 * @return true if it is
	 */
	public static boolean is(ItemStack is, Material material, int data)
	{
		return is(is, material, (byte) data);
	}

	/**
	 * Does the item have meta
	 *
	 * @param is
	 *            the item
	 * @return true if it does
	 */
	public static boolean hasMeta(ItemStack is)
	{
		return is(is) && is.hasItemMeta();
	}

	/**
	 * Does the item have a custom name
	 *
	 * @param is
	 *            the item
	 * @return true if it has a name
	 */
	public static boolean hasName(ItemStack is)
	{
		return hasMeta(is) && is.getItemMeta().hasDisplayName();
	}

	/**
	 * Does the item have any lore?
	 *
	 * @param is
	 *            the item
	 * @return true if it does
	 */
	public static boolean hasLore(ItemStack is)
	{
		return hasMeta(is) && is.getItemMeta().hasLore();
	}

	/**
	 * Does the item have the given name (color matters)
	 *
	 * @param is
	 *            the item
	 * @param name
	 *            the name
	 * @return true if it has the name
	 */
	public static boolean hasName(ItemStack is, String name)
	{
		return hasName(is) && is.getItemMeta().getDisplayName().equals(name);
	}

	/**
	 * Does the item have the exact lore
	 *
	 * @param is
	 *            the item
	 * @param lores
	 *            the lore
	 * @return true if it does
	 */
	public static boolean hasLore(ItemStack is, List<String> lores)
	{
		return hasLore(is) && new GList<String>(lores).equals(new GList<String>(is.getItemMeta().getLore()));
	}

	/**
	 * Does the item have the given enchantment
	 *
	 * @param is
	 *            the item
	 * @param e
	 *            the enchantment
	 * @return true if it does
	 */
	public static boolean hasEnchantment(ItemStack is, Enchantment e)
	{
		return is(is) && is.getEnchantments().containsKey(e);
	}

	/**
	 * Does the item have the enchantment at the given level
	 *
	 * @param is
	 *            the item
	 * @param e
	 *            the enchantment
	 * @param level
	 *            the level
	 * @return true if it does
	 */
	public static boolean hasEnchantment(ItemStack is, Enchantment e, int level)
	{
		if(!is(is))
		{
			return false;
		}

		return hasEnchantment(is, e) && is.getEnchantmentLevel(e) == level;
	}

	/**
	 * Does the item have any enchantments
	 *
	 * @param is
	 *            the item
	 * @return true if it does
	 */
	public static boolean hasEnchantments(ItemStack is)
	{
		if(!is(is))
		{
			return false;
		}

		return !is.getEnchantments().isEmpty();
	}

	/**
	 * Get a materialblock representation of this item
	 *
	 * @param is
	 *            the item
	 * @return the materialblock or null if the item is null
	 */
	@SuppressWarnings("deprecation")
	public static MaterialBlock toMaterialBlock(ItemStack is)
	{
		if(is != null)
		{
			return new MaterialBlock(is.getType(), is.getData().getData());
		}

		return null;
	}

	/**
	 * Should the itemstack be broken?
	 *
	 * @param is
	 *            the itemStack
	 * @return true if it should be broken
	 */
	public static boolean isBroken(ItemStack is)
	{
		return is(is) && getMaxDurability(is) == getDurability(is) && hasDurability(is);
	}

	/**
	 * Does this item have durability
	 *
	 * @param is
	 *            the item
	 * @return true if it does
	 */
	public static boolean hasDurability(ItemStack is)
	{
		return is(is) && getMaxDurability(is) > 0;
	}

	/**
	 * Get the durability percent
	 *
	 * @param is
	 *            the itemstack
	 * @return the percent
	 */
	public static double getDurabilityPercent(ItemStack is)
	{
		if(!is(is))
		{
			return 0.0;
		}

		if(getMaxDurability(is) == 0)
		{
			return 1.0;
		}

		return 1.0 - ((double) getDurability(is) / (double) getMaxDurability(is));
	}

	/**
	 * Set the durability percent
	 *
	 * @param is
	 *            the itemStack
	 * @param pc
	 *            the percent
	 */
	public static void setDurabilityPercent(ItemStack is, double pc)
	{
		if(!is(is))
		{
			return;
		}

		pc = (pc > 1.0 ? 1.0 : (pc < 0.0 ? 0.0 : pc));

		if(getDurability(is) == 0)
		{
			return;
		}

		setDurability(is, (int) ((double) getMaxDurability(is) * (1.0 - pc)));
	}

	/**
	 * Get the max durability
	 *
	 * @param is
	 *            the item
	 * @return the item type's max durability
	 */
	public static short getMaxDurability(ItemStack is)
	{
		if(!is(is))
		{
			return 0;
		}

		return is.getType().getMaxDurability();
	}

	/**
	 * Get the durability
	 *
	 * @param is
	 *            the item
	 * @return the item durability
	 */
	public static short getDurability(ItemStack is)
	{
		if(!is(is))
		{
			return 0;
		}

		return is.getDurability();
	}

	/**
	 * Set the durability (if higher than max, it will be set to the max)
	 *
	 * @param is
	 *            the item
	 * @param dmg
	 *            the durability
	 */
	public static void setDurability(ItemStack is, short dmg)
	{
		if(!is(is))
		{
			return;
		}

		is.setDurability(dmg > getMaxDurability(is) ? getMaxDurability(is) : dmg);
	}

	/**
	 * Set the durability (if higher than max, it will be set to the max)
	 *
	 * @param is
	 *            the item
	 * @param dmg
	 *            the durability
	 */
	public static void setDurability(ItemStack is, int dmg)
	{
		if(!is(is))
		{
			return;
		}

		setDurability(is, (short) dmg);
	}

	/**
	 * Damage an itemstack
	 *
	 * @param is
	 *            the item
	 * @param amt
	 *            the amount to damage
	 */
	public static void damage(ItemStack is, int amt)
	{
		if(!is(is))
		{
			return;
		}

		setDurability(is, getDurability(is) + amt);
	}

	/**
	 * Can the item a be stacked onto the item b (following max stack size)
	 *
	 * @param a
	 *            the item a
	 * @param b
	 *            the item b
	 * @return true if they can be merged
	 */
	@SuppressWarnings("deprecation")
	public static boolean isMergable(ItemStack a, ItemStack b)
	{
		if(is(a) && is(b))
		{
			if(!a.getType().equals(b.getType()))
			{
				return false;
			}

			if(a.getData().getData() != b.getData().getData())
			{
				return false;
			}

			if(a.hasItemMeta() != b.hasItemMeta())
			{
				return false;
			}

			if(a.getDurability() != b.getDurability())
			{
				return false;
			}

			if(a.hasItemMeta())
			{
				if(!a.getItemMeta().getDisplayName().equals(b.getItemMeta().getDisplayName()))
				{
					return false;
				}

				if(!new GList<String>(a.getItemMeta().getLore()).equals(new GList<String>(b.getItemMeta().getLore())))
				{
					return false;
				}
			}

			if(a.getMaxStackSize() < a.getAmount() + b.getAmount())
			{
				return false;
			}

			return true;
		}

		return false;
	}

	public static boolean addItem(Player player, ItemStack is)
	{
		for(int i = 0; i < 9; i++)
		{
			if(player.getInventory().getItem(i) == null)
			{
				player.getInventory().setHeldItemSlot(i);
				player.getInventory().setItem(i, is);
				return true;
			}
		}

		return false;
	}
}