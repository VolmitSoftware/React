package primal.bukkit.world;

import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import primal.lang.collection.GList;
import primal.lang.collection.GListAdapter;

/**
 * Player utilities
 *
 * @author cyberpwn
 */
public class Players
{
	public static void messageWithPerms(String message, String... permissions)
	{
		message(getPlayersWithPermission(permissions), message);
	}

	public static void messageWithoutPerms(String message, String... permissions)
	{
		message(getPlayersWithoutPermission(permissions), message);
	}

	public static void messageOps(String message)
	{
		message(getOps(), message);
	}

	public static void messageNonOps(String message)
	{
		message(getNonOps(), message);
	}

	public static void message(GList<Player> p, String msg)
	{
		for(Player i : onlinePlayers())
		{
			i.sendMessage(msg);
		}
	}

	/**
	 * Is the given player online?
	 *
	 * @param player
	 *            the player
	 * @return true if the player is
	 */
	public static boolean isOnline(String player)
	{
		return getPlayer(player) != null;
	}

	/**
	 * Get the given player
	 *
	 * @param player
	 *            the player name
	 * @return the player or null
	 */
	public static Player getPlayer(String player)
	{
		return Bukkit.getPlayer(player);
	}

	/**
	 * Get the off hand
	 *
	 * @param p
	 *            the player
	 * @return the item
	 */
	public static ItemStack getOffHand(Player p)
	{
		return p.getInventory().getItemInOffHand();
	}

	/**
	 * Get the main hand
	 *
	 * @param p
	 *            the player
	 * @return the item
	 */
	public static ItemStack getMainHand(Player p)
	{
		return p.getInventory().getItemInMainHand();
	}

	/**
	 * Set off hand
	 *
	 * @param p
	 *            the player
	 * @param is
	 *            the item stack
	 */
	public static void setOffHand(Player p, ItemStack is)
	{
		p.getInventory().setItemInOffHand(is);
	}

	/**
	 * Set main hand
	 *
	 * @param p
	 *            the player
	 * @param is
	 *            the item stack
	 */
	public static void setMainHand(Player p, ItemStack is)
	{
		p.getInventory().setItemInMainHand(is);
	}

	/**
	 * Swap hands (off and main items)
	 *
	 * @param p
	 *            the player
	 */
	public static void swapHands(Player p)
	{
		ItemStack main = getMainHand(p).clone();
		setMainHand(p, getOffHand(p));
		setOffHand(p, main);
	}

	/**
	 * Is there any player online?
	 *
	 * @return true if at least one player is online
	 */
	public static boolean isAnyOnline()
	{
		return !Bukkit.getOnlinePlayers().isEmpty();
	}

	/**
	 * Get all players in the given world
	 *
	 * @param world
	 *            the world
	 * @return the players
	 */
	public static GList<Player> inWorld(World world)
	{
		return new GList<Player>(world.getPlayers());
	}

	/**
	 * Get all players in the given chunk
	 *
	 * @param chunk
	 *            the chunk
	 * @return the list of players
	 */
	public static GList<Player> inChunk(Chunk chunk)
	{
		return new GList<Player>(new GListAdapter<Entity, Player>()
		{
			@Override
			public Player onAdapt(Entity from)
			{
				if(from.getType().equals(EntityType.PLAYER))
				{
					return (Player) from;
				}

				return null;
			}
		}.adapt(new GList<Entity>(chunk.getEntities())));
	}

	/**
	 * Get all players in the given area
	 *
	 * @param l
	 *            the center location
	 * @param radius
	 *            the three dimensional area radius to search
	 * @return a list of players in the given area
	 */
	public static GList<Player> inArea(Location l, double radius)
	{
		return new GList<Player>(new Area(l, radius).getNearbyPlayers());
	}

	/**
	 * Get all players in the given area
	 *
	 * @param l
	 *            the center location
	 * @param radius
	 *            the three dimensional area radius to search
	 * @return a list of players in the given area
	 */
	public static GList<Player> inArea(Location l, int radius)
	{
		return new GList<Player>(new Area(l, radius).getNearbyPlayers());
	}

	/**
	 * Checks if anyone is online
	 *
	 * @return returns true if there is at least one player online.
	 */
	public static boolean isAnyoneOnline()
	{
		return !getPlayers().isEmpty();
	}

	/**
	 * Get the player count
	 *
	 * @return the player count
	 */
	public static int getPlayerCount()
	{
		return getPlayers().size();
	}

	/**
	 * Randomly pick a player
	 *
	 * @return a random player or null if no one is online.
	 */
	public static Player getRandomPlayer()
	{
		if(isAnyoneOnline())
		{
			return getPlayers().pickRandom();
		}

		return null;
	}

	/**
	 * Get the player based on uuid
	 *
	 * @param id
	 *            the player uid
	 * @return the player matching the uuid or null
	 */
	public static Player getPlayer(UUID id)
	{
		return Bukkit.getPlayer(id);
	}

	/**
	 * Search for multiple player matches. If there is an identical match, nothing
	 * else will be searched. If there is multiple ignored case matches, partials
	 * will not be matched. Else it will match all partials.
	 *
	 * @param search
	 *            the search query
	 * @return a list of partial matches
	 */
	public GList<Player> getPlayers(String search)
	{
		GList<Player> players = getPlayers();
		GList<Player> found = new GList<Player>();

		for(Player i : players)
		{
			if(i.getName().equals(search))
			{
				found.add(i);
			}
		}

		if(!found.isEmpty())
		{
			return found.removeDuplicates();
		}

		for(Player i : players)
		{
			if(i.getName().equalsIgnoreCase(search))
			{
				found.add(i);
			}
		}

		if(!found.isEmpty())
		{
			return found.removeDuplicates();
		}

		for(Player i : players)
		{
			if(i.getName().toLowerCase().contains(search.toLowerCase()))
			{
				found.add(i);
			}
		}

		return found.removeDuplicates();
	}

	/**
	 * Returns a list of ops
	 *
	 * @return ops in glist
	 */
	public static GList<Player> getOps()
	{
		return getPlayers(new GListAdapter<Player, Player>()
		{
			@Override
			public Player onAdapt(Player from)
			{
				return from.isOp() ? from : null;
			}
		});
	}

	/**
	 * Returns a list of non ops
	 *
	 * @return non op list
	 */
	public static GList<Player> getNonOps()
	{
		return getPlayers(new GListAdapter<Player, Player>()
		{
			@Override
			public Player onAdapt(Player from)
			{
				return from.isOp() ? null : from;
			}
		});
	}

	/**
	 * Returns a list of players who have all of the given permissions.
	 *
	 * @param permissions
	 *            a collection of permissions any player in the return list must
	 *            have. Supplying no permissions will return the source list.
	 * @return a glist of players
	 */
	public static GList<Player> getPlayersWithPermission(String... permissions)
	{
		return getPlayers(new GListAdapter<Player, Player>()
		{
			@Override
			public Player onAdapt(Player from)
			{
				for(String i : permissions)
				{
					if(!from.hasPermission(i))
					{
						return null;
					}
				}

				return from;
			}
		});
	}

	/**
	 * Returns a list of players who do not have all of the given permissions.
	 *
	 * @param permissions
	 *            a collection of permissions any player in the return list cannot
	 *            have. Supplying no permissions will return the source list.
	 * @return a glist of players
	 */
	public static GList<Player> getPlayersWithoutPermission(String... permissions)
	{
		return getPlayers(new GListAdapter<Player, Player>()
		{
			@Override
			public Player onAdapt(Player from)
			{
				for(String i : permissions)
				{
					if(from.hasPermission(i))
					{
						return null;
					}
				}

				return from;
			}
		});
	}

	/**
	 * Get a list of players who match the given adapter. Source list comes from all
	 * players currently connected.
	 *
	 * @param adapter
	 *            the glist adapter to determine if the player should be adapted to
	 *            the next list. If the adapter returns null instead of the source
	 *            player, it will not be added to the list.
	 * @return the adapted list of players
	 */
	public static GList<Player> getPlayers(GListAdapter<Player, Player> adapter)
	{
		return (GList<Player>) adapter.adapt(getPlayers());
	}

	/**
	 * Returns a glist of all players currently online
	 *
	 * @return a glist representing all online players
	 */
	public static GList<Player> getPlayers()
	{
		GList<Player> p = new GList<Player>();

		for(Player i : Bukkit.getOnlinePlayers())
		{
			p.add(i);
		}

		return p;
	}

	public static Player getAnyPlayer()
	{
		if(!isAnyOnline())
		{
			return null;
		}

		return getPlayers().get(0);
	}

	public static boolean isWithinViewDistance(Player p, Chunk c)
	{
		int manhattan = (int) (Bukkit.getViewDistance() * 1.5);
		int mdist = Math.abs(p.getLocation().getChunk().getX() - c.getX()) + Math.abs(p.getLocation().getChunk().getZ() - c.getZ());

		return mdist <= manhattan;
	}

	public static boolean isWithinViewDistance(Chunk c)
	{
		for(Player i : c.getWorld().getPlayers())
		{
			if(isWithinViewDistance(i, c))
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * Disable the player's ability to see
	 *
	 * @param p
	 *            the player to disable
	 */
	public static void disable(Player p)
	{
		PE.BLINDNESS.a(500).d(1024).c(p);
	}

	/**
	 * Remove disabled effect
	 *
	 * @param p
	 *            the player to remove it from
	 */
	public static void enable(Player p)
	{
		PE.BLINDNESS.a(500).d(20).rm(p);
	}

	/**
	 * Get the target block
	 *
	 * @param p
	 *            the player
	 * @param distance
	 *            the max distance
	 * @return the location
	 */
	public static Location targetBlock(Player p, int distance)
	{
		return p.getTargetBlock((Set<Material>) null, distance).getLocation().clone().add(0.5, 0.5, 0.5);
	}

	/**
	 * Get the target entity the player is looking at
	 *
	 * @param p
	 *            the player
	 * @param distance
	 *            the max distance
	 * @return the entity or null
	 */
	public static Entity targetEntity(Player p, int distance)
	{
		return getEntityLookingAt(p, distance, 0.15);
	}

	/**
	 * Does the player have an inventory open
	 *
	 * @param player
	 *            the player
	 * @return true if the player does
	 */
	public static boolean hasInventoryOpen(final Player player)
	{
		final InventoryView view = player.getOpenInventory();
		return view != null && view.getType() != InventoryType.CRAFTING;
	}

	/**
	 * Get the target entity of the player
	 *
	 * @param e
	 *            the player
	 * @param range
	 *            the max range
	 * @param off
	 *            the offset
	 * @return the entity or null
	 */
	public static Entity getEntityLookingAt(Player e, double range, double off)
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

		new RayTrace(e.getLocation().clone().add(0.5, 1.5, 0.5), e.getLocation().getDirection(), range, (double) 1)
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
	 * Can you find a player with the search?
	 *
	 * @param search
	 *            the search
	 * @return true if a player can be found
	 */
	public static boolean canFindPlayer(String search)
	{
		return findPlayer(search) == null ? false : true;
	}

	/**
	 * Find a player
	 *
	 * @param search
	 *            the search
	 * @return the player or null
	 */
	public static Player findPlayer(String search)
	{
		for(Player i : onlinePlayers())
		{
			if(i.getName().equalsIgnoreCase(search))
			{
				return i;
			}
		}

		for(Player i : onlinePlayers())
		{
			if(i.getName().toLowerCase().contains(search.toLowerCase()))
			{
				return i;
			}
		}

		return null;
	}

	/**
	 * Get online players
	 *
	 * @return the players
	 */
	public static GList<Player> onlinePlayers()
	{
		GList<Player> px = new GList<Player>();

		for(Player i : Bukkit.getOnlinePlayers())
		{
			px.add(i);
		}

		return px;
	}

	/**
	 * Get the location of the player's crotch (it's needed sometimes)
	 *
	 * @param p
	 *            the player
	 * @return playerjunk
	 */
	public static Location getCrotchLocation(Player p)
	{
		return p.getLocation().add(0, 0.899, 0).add(p.getLocation().getDirection().setY(0).multiply(0.1));
	}

	/**
	 * Clear maxhealth, potion effects, speed and more
	 *
	 * @param p
	 *            the player
	 */
	public static void clear(Player p)
	{
		resetMaxHeath(p);
		resetHunger(p);
		heal(p);
	}

	/**
	 * Clear player potion effects
	 *
	 * @param p
	 *            the player
	 */
	public static void clearEffects(Player p)
	{
		for(PotionEffect i : new GList<PotionEffect>(p.getActivePotionEffects()))
		{
			p.removePotionEffect(i.getType());
		}
	}

	/**
	 * Heal the player an amount
	 *
	 * @param p
	 *            the player
	 * @param health
	 *            the health
	 */
	@SuppressWarnings("deprecation")
	public static void heal(Player p, double health)
	{
		p.setHealth(p.getHealth() + health > p.getMaxHealth() ? p.getMaxHealth() : p.getHealth() + health);
	}

	/**
	 * Heal the player to max health
	 *
	 * @param p
	 *            the player
	 */
	public static void heal(Player p)
	{
		p.setHealth(p.getHealth());
	}

	/**
	 * Reset the player max health
	 *
	 * @param p
	 *            the player
	 */
	@SuppressWarnings("deprecation")
	public static void resetMaxHeath(Player p)
	{
		p.setMaxHealth(20);
	}

	/**
	 * Resets the player hunger
	 *
	 * @param p
	 *            the hunger
	 */
	public static void resetHunger(Player p)
	{
		p.setFoodLevel(20);
	}

	/**
	 * Kill the player
	 *
	 * @param p
	 *            the player p
	 */
	public static void kill(Player p)
	{
		p.setHealth(0);
	}

	/**
	 * Get the area of the player in the form of a shape
	 *
	 * @param p
	 *            the player
	 * @return the shape
	 */
	public static Shape getShape(Player p)
	{
		return new Shape(getCrotchLocation(p), new Vector(0.7, 1.8, 0.7));
	}

	/**
	 * Get the 1st person hand.
	 *
	 * @param p
	 *            the player
	 * @return the estimate location of their hand
	 */
	public static Location getHand(Player p)
	{
		return getHand(p, 0f, 0f);
	}

	/**
	 * Get the 1st person hand.
	 *
	 * @param p
	 *            the player
	 * @param yawShift
	 *            the shift yaw
	 * @param pitchShift
	 *            the shift pitch
	 * @return the location
	 */
	public static Location getHand(Player p, float yawShift, float pitchShift)
	{
		Location base = p.getEyeLocation();
		Location mode = p.getEyeLocation();
		Float yaw = p.getLocation().getYaw() + 50 + yawShift;
		Float pitch = p.getLocation().getPitch() + pitchShift;

		mode.setYaw(yaw);
		mode.setPitch(pitch);
		base.add(mode.getDirection());

		return base;
	}
}
