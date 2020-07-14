package primal.bukkit.world;

import java.util.Collection;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.material.MaterialData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import primal.lang.collection.GList;

/**
 * World utils
 *
 * @author cyberpwn
 */
public class W
{
	/**
	 * Could this block be seen based on if there is air surrounding it or not?
	 *
	 * @param block
	 *            the block
	 * @return returns true if you could see this block ingame from a position
	 */
	public static boolean visible(Block block)
	{
		for(Block i : W.blockFaces(block))
		{
			if(i.getType().isTransparent())
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * Get the average location
	 *
	 * @param ll
	 *            the locations
	 * @return the average location
	 */
	public static Location getAverageLocation(GList<Location> ll)
	{
		double x = 0;
		double y = 0;
		double z = 0;
		World w = null;

		for(Location i : ll)
		{
			w = i.getWorld();
			x += i.getX();
			y += i.getY();
			z += i.getZ();
		}

		return new Location(w, x / (double) ll.size(), y / (double) ll.size(), z / (double) ll.size());
	}

	/**
	 * Set the color of a peice of armor
	 *
	 * @param s
	 *            the item stack
	 * @param c
	 *            the color
	 */
	public static void colorArmor(ItemStack s, Color c)
	{
		LeatherArmorMeta lam = (LeatherArmorMeta) s.getItemMeta();
		lam.setColor(c);
		s.setItemMeta(lam);
	}

	/**
	 * Get the entity from the entity id
	 *
	 * @param entityId
	 *            the entity id
	 * @return the entity or null
	 */
	public static Entity getEntity(int entityId)
	{
		for(World i : Bukkit.getWorlds())
		{
			for(Entity j : i.getEntities())
			{
				if(j.getEntityId() == entityId)
				{
					return j;
				}
			}
		}

		return null;
	}

	/**
	 * Get the amount of the given item a player has
	 *
	 * @param p
	 *            the player
	 * @param mb
	 *            the materialblock
	 * @return the amount they have
	 */
	@SuppressWarnings("deprecation")
	public static int count(Player p, MaterialBlock mb)
	{
		int has = 0;

		for(ItemStack i : p.getInventory().getContents())
		{
			if(i != null)
			{
				if(!i.hasItemMeta() && i.getType().equals(mb.getMaterial()) && (i.getData().getData() == -1 ? 0 : i.getData().getData()) == mb.getData())
				{
					has += i.getAmount();
				}
			}
		}

		return has;
	}

	/**
	 * Check if the player has a certain amount of items in their inventory (or
	 * more)
	 *
	 * @param p
	 *            the player
	 * @param mb
	 *            the material block
	 * @param amt
	 *            the amount they need at least
	 * @return true if they have enough
	 */
	public static boolean has(Player p, MaterialBlock mb, int amt)
	{
		return count(p, mb) >= amt;
	}

	/**
	 * Get the block coord for the given chunk (0-15)
	 *
	 * @param b
	 *            the block
	 * @return the coord
	 */
	public static int getChunkX(Block b)
	{
		return b.getX() - (b.getChunk().getX() << 4);
	}

	/**
	 * Get the block coord for the given chunk (0-255)
	 *
	 * @param b
	 *            the block
	 * @return the coord
	 */
	public static int getChunkY(Block b)
	{
		return b.getY();
	}

	/**
	 * Drop an experience orb
	 *
	 * @param location
	 *            the location
	 * @param xp
	 *            the amount of xp
	 */
	public static void dropXp(Location location, int xp)
	{
		((ExperienceOrb) location.getWorld().spawn(location, ExperienceOrb.class)).setExperience(xp);
	}

	/**
	 * Get the block coord for the given chunk (0-15)
	 *
	 * @param b
	 *            the block
	 * @return the coord
	 */
	public static int getChunkZ(Block b)
	{
		return b.getZ() - (b.getChunk().getZ() << 4);
	}

	/**
	 * Take items from the player's inventory
	 *
	 * @param p
	 *            the player
	 * @param mb
	 *            the type
	 * @param amt
	 *            the amount
	 */
	@SuppressWarnings("deprecation")
	public static void take(Player p, MaterialBlock mb, int amt)
	{
		if(has(p, mb, amt))
		{
			for(int i = 0; i < amt; i++)
			{
				p.getInventory().removeItem(new ItemStack(mb.getMaterial(), 1, (short) 0, mb.getData()));
			}
		}
	}

	/**
	 * Calculates the so-called 'Manhatten Distance' between two locations<br>
	 * This is the distance between two points without going diagonally
	 *
	 * @param b1
	 *            location
	 * @param b2
	 *            location
	 * @param checkY
	 *            state, True to include the y distance, False to exclude it
	 * @return The manhattan distance
	 */
	public static int getManhattanDistance(Location b1, Location b2, boolean checkY)
	{
		int d = Math.abs(b1.getBlockX() - b2.getBlockX());
		d += Math.abs(b1.getBlockZ() - b2.getBlockZ());

		if(checkY)
		{
			d += Math.abs(b1.getBlockY() - b2.getBlockY());
		}

		return d;
	}

	public static GList<Location> news(Location location)
	{
		GList<Location> news = new GList<Location>();

		news.add(location.clone().add(1, 0, 0));
		news.add(location.clone().add(0, 0, 1));
		news.add(location.clone().add(-1, 0, 0));
		news.add(location.clone().add(0, 0, -1));

		return news;
	}

	/**
	 * Calculates the so-called 'Manhatten Distance' between two blocks<br>
	 * This is the distance between two points without going diagonally
	 *
	 * @param b1
	 *            block
	 * @param b2
	 *            block
	 * @param checkY
	 *            state, True to include the y distance, False to exclude it
	 * @return The Manhattan distance
	 */
	public static int getManhattanDistance(Block b1, Block b2, boolean checkY)
	{
		int d = Math.abs(b1.getX() - b2.getX());
		d += Math.abs(b1.getZ() - b2.getZ());

		if(checkY)
		{
			d += Math.abs(b1.getY() - b2.getY());
		}

		return d;
	}

	/**
	 * Get all blocks from a chunk
	 *
	 * @param c
	 *            the chunk
	 * @return the blocks in the form of a list
	 */
	public static GList<Block> getBlocks(Chunk c)
	{
		return new GList<Block>(new Cuboid(c.getBlock(0, 0, 0).getLocation(), c.getBlock(15, 255, 15).getLocation()).iterator());
	}

	/**
	 * Get a list of chunklets from the given chunks (always 16)
	 *
	 * @param c
	 *            the chunk
	 * @return the 4x4 chunklets
	 */
	public static GList<Chunklet> getChunklets(Chunk c)
	{
		GList<Chunklet> cx = new GList<Chunklet>();

		for(int i = 0; i < 16; i += 4)
		{
			for(int j = 0; j < 16; j += 4)
			{
				cx.add(new Chunklet(c.getBlock(i, 0, j).getLocation()));
			}
		}

		return cx;
	}

	/**
	 * Get a list of chunklets from a list of chunks
	 *
	 * @param c
	 *            the list of chunks
	 * @return the list of chunklets with the size of (16 * c.size())
	 */
	public static GList<Chunklet> getChunklets(GList<Chunk> c)
	{
		GList<Chunklet> cx = new GList<Chunklet>();

		for(Chunk i : c)
		{
			cx.add(getChunklets(i));
		}

		return cx;
	}

	/**
	 * Tries to get a material from a string including meta (excluding it implies 0)
	 * </br>
	 * </br>
	 * 1, STONE, stone, 1:0 all would return STONE (byte = 0)
	 *
	 * @param s
	 *            the input string
	 * @return the MaterialBlock, NULL if it cannot parse.
	 */
	@SuppressWarnings("deprecation")
	public static MaterialBlock getMaterialBlock(String s)
	{
		Material material = null;
		Byte meta = (byte) 0;
		String m = "0";
		String b = "0";

		if(s.contains(":"))
		{
			m = s.split(":")[0];
			b = s.split(":")[1];
		}

		else
		{
			m = s;
		}

		try
		{
			material = Material.getMaterial(Integer.valueOf(m));

			if(material == null)
			{
				try
				{
					material = Material.valueOf(m.toUpperCase());

					if(material == null)
					{
						return null;
					}
				}

				catch(Exception e)
				{
					return null;
				}
			}
		}

		catch(Exception e)
		{
			try
			{
				material = Material.valueOf(m.toUpperCase());

				if(material == null)
				{
					return null;
				}
			}

			catch(Exception ex)
			{
				return null;
			}
		}

		try
		{
			meta = Integer.valueOf(b).byteValue();
		}

		catch(Exception e)
		{
			meta = (byte) 0;
		}

		return new MaterialBlock(material, meta);
	}

	/**
	 * Gets all the Blocks relative to a main block using multiple Block Faces
	 *
	 * @param main
	 *            block
	 * @param faces
	 *            to get the blocks relative to the main of
	 * @return An array of relative blocks to the main based on the input faces
	 */
	public static Block[] getRelative(Block main, BlockFace... faces)
	{
		if(main == null)
		{
			return new Block[0];
		}

		Block[] rval = new Block[faces.length];

		for(int i = 0; i < rval.length; i++)
		{
			rval[i] = main.getRelative(faces[i]);
		}

		return rval;
	}

	/**
	 * Sets the Block type and data at once, then performs physics
	 *
	 * @param block
	 *            to set the type and data of
	 * @param type
	 *            to set to
	 * @param data
	 *            to set to
	 */
	public static void setTypeAndData(Block block, Material type, MaterialData data)
	{
		setTypeAndData(block, type, data, true);
	}

	/**
	 * Sets the Block type and data at once
	 *
	 * @param block
	 *            to set the type and data of
	 * @param type
	 *            to set to
	 * @param data
	 *            to set to
	 * @param update
	 *            - whether to perform physics afterwards
	 */
	@SuppressWarnings("deprecation")
	public static void setTypeAndData(Block block, Material type, MaterialData data, boolean update)
	{
		block.setTypeIdAndData(type.getId(), data.getData(), update);
	}

	/**
	 * Sets the Block type and data at once, then performs physics
	 *
	 * @param block
	 *            to set the type and data of
	 * @param type
	 *            to set to
	 * @param data
	 *            to set to
	 */
	public static void setTypeAndRawData(Block block, Material type, int data)
	{
		setTypeAndRawData(block, type, data, true);
	}

	/**
	 * Sets the Block type and data at once
	 *
	 * @param block
	 *            to set the type and data of
	 * @param type
	 *            to set to
	 * @param data
	 *            to set to
	 * @param update
	 *            - whether to perform physics afterwards
	 */
	@SuppressWarnings("deprecation")
	public static void setTypeAndRawData(Block block, Material type, int data, boolean update)
	{
		block.setTypeIdAndData(type.getId(), (byte) data, update);
	}

	/**
	 * Sets the Material Data for a Block
	 *
	 * @param block
	 *            to set it for
	 * @param materialData
	 *            to set to
	 */
	@SuppressWarnings("deprecation")
	public static void setData(Block block, MaterialData materialData)
	{
		block.setData(materialData.getData());
	}

	/**
	 * Sets the Material Data for a Block
	 *
	 * @param block
	 *            to set it for
	 * @param materialData
	 *            to set to
	 * @param doPhysics
	 *            - True to perform physics, False for 'silent'
	 */
	@SuppressWarnings("deprecation")
	public static void setData(Block block, MaterialData materialData, boolean doPhysics)
	{
		block.setData(materialData.getData(), doPhysics);
	}

	/**
	 * Gets the highest level of a type of potion from a list of potion effect
	 * objects
	 *
	 * @param type
	 *            the potion effect type
	 * @param pots
	 *            the potions collection
	 * @return the level. This is one added to the amplifier. So if there are NO
	 *         potion effects of that type, 0 is returned. However if the highest
	 *         amplifier is 0, then 1 will be returned and so on.
	 */
	public static int getHighestPotionLevel(PotionEffectType type, Collection<PotionEffect> pots)
	{
		int highest = 0;

		for(PotionEffect i : pots)
		{
			if(i.getType().equals(type) && i.getAmplifier() + 1 > highest)
			{
				highest = i.getAmplifier() + 1;
			}
		}

		return highest;
	}

	/**
	 * Chunk faces around a given chunk
	 *
	 * @param c
	 *            the chunk
	 * @return the surrounding 4 chunks
	 */
	public static GList<Chunk> chunkFaces(Chunk c)
	{
		GList<Chunk> cx = new GList<Chunk>();

		cx.add(c.getWorld().getChunkAt(c.getX() + 1, c.getZ()));
		cx.add(c.getWorld().getChunkAt(c.getX() - 1, c.getZ()));
		cx.add(c.getWorld().getChunkAt(c.getX(), c.getZ() + 1));
		cx.add(c.getWorld().getChunkAt(c.getX(), c.getZ() - 1));

		return cx;
	}

	/**
	 * Get all 6 blocks touching a given block
	 *
	 * @param b
	 *            the block
	 * @return the surrounding 6 blocks
	 */
	public static GList<Block> blockFaces(Block b)
	{
		GList<Block> blocks = new GList<Block>();

		blocks.add(b.getRelative(BlockFace.UP));
		blocks.add(b.getRelative(BlockFace.DOWN));
		blocks.add(b.getRelative(BlockFace.NORTH));
		blocks.add(b.getRelative(BlockFace.SOUTH));
		blocks.add(b.getRelative(BlockFace.EAST));
		blocks.add(b.getRelative(BlockFace.WEST));

		return blocks;
	}

	/**
	 * simulate a fall from a location
	 *
	 * @param from
	 *            the location to fall from
	 * @return the location where it would fall to
	 */
	public static Location simulateFall(Location from)
	{
		int height = from.getBlockY();

		for(int i = height; i > 0; i--)
		{
			int check = i - 1;

			Material type = new Location(from.getWorld(), from.getBlockX(), check, from.getBlockZ()).getBlock().getType();

			if(!(type.equals(Material.AIR) || type.equals(Material.WATER) || type.equals(Material.STATIONARY_WATER) || type.equals(Material.LAVA) || type.equals(Material.STATIONARY_LAVA)))
			{
				return new Location(from.getWorld(), from.getBlockX(), check + 1, from.getBlockZ());
			}
		}

		return null;
	}

	/**
	 * Get a radius area of chunks around a given chunk
	 *
	 * @param c
	 *            the chunk center
	 * @param rad
	 *            the radius
	 * @return the chunks including the center given chunk
	 */
	public static GList<Chunk> chunkRadius(Chunk c, int rad)
	{
		GList<Chunk> cx = new GList<Chunk>();

		for(int i = c.getX() - rad + 1; i < c.getX() + rad; i++)
		{
			for(int j = c.getZ() - rad + 1; j < c.getZ() + rad; j++)
			{
				cx.add(c.getWorld().getChunkAt(i, j));
			}
		}

		cx.add(c);

		return cx;
	}

	/**
	 * Get a radius area of blocks around a given chunk
	 *
	 * @param c
	 *            the block center
	 * @param rad
	 *            the radius
	 * @return the blocks including the center given block
	 */
	public static GList<Block> blockRadius(Block c, int rad)
	{
		GList<Block> cx = new GList<Block>();

		for(int i = c.getX() - rad + 1; i < c.getX() + rad; i++)
		{
			for(int j = c.getZ() - rad + 1; j < c.getZ() + rad; j++)
			{
				cx.add(c.getWorld().getBlockAt(i, c.getY(), j));
			}
		}

		cx.add(c);

		return cx;
	}

	/**
	 * Get an entity that the supplied entity (e) is looking at with a specific
	 * range and offset
	 *
	 * @param e
	 *            the entity
	 * @param range
	 *            the max range to check for. If this is less than 1, 1 will be used
	 *            instead.
	 * @param off
	 *            the offeset. For example, if this is set to 2, then you cannot be
	 *            looking at an entity if it is at least 3 or more blocks away from
	 *            your target. If the offset is less than 1, 1 will be used instead
	 * @return an entity that the supplied entity (e) is looking at. If the supplied
	 *         entity is not looking at an entity, or it does not meet the given
	 *         ranges and offsets, null will be returned instead
	 */
	public static Entity getEntityLookingAt(Entity e, double range, double off)
	{
		if(off < 1)
		{
			off = 1;
		}

		if(range < 1)
		{
			range = 1;
		}

		final Double doff = off;
		final Entity[] result = new Entity[1];

		new RayTrace(e.getLocation().clone().add(0.5, 0.5, 0.5), e.getLocation().getDirection(), range, (double) 1)
		{
			@Override
			public void onTrace(Location l)
			{
				Area a = new Area(l, doff);

				for(Entity i : a.getNearbyEntities())
				{
					if(!e.equals(i))
					{
						stop();
						result[0] = i;
						return;
					}
				}
			}
		}.trace();

		return result[0];
	}

	/**
	 * Check if the given entity IS is looking at the given entity AT.
	 *
	 * @param is
	 *            the entity
	 * @param at
	 *            the entity that IS should be looking at to return true
	 * @param range
	 *            the max range to check
	 * @param off
	 *            the max offset
	 * @return true if the entity IS is looking at the given entity AT
	 */
	public static boolean isLookingAt(Entity is, Entity at, double range, double off)
	{
		Entity e = getEntityLookingAt(is, range, off);

		if(e == null)
		{
			return false;
		}

		return e.equals(at);
	}

	/**
	 * Get the difference between two vectors (squared)
	 *
	 * @param a
	 *            the first vector
	 * @param b
	 *            the second vector
	 * @return the difference
	 */
	public static double differenceOfVectors(Vector a, Vector b)
	{
		return a.distanceSquared(b);
	}
}