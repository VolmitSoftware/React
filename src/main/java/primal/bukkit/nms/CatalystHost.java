package primal.bukkit.nms;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import primal.bukkit.world.MaterialBlock;
import primal.util.text.C;

public interface CatalystHost extends PacketListener, Listener
{
	public void sendItemStack(Player p, ItemStack is, int slot);

	public void sendEntityMetadata(Player p, int eid, Object... objects);

	public void sendEntityMetadata(Player p, int eid, List<Object> objects);

	public Object getMetaEntityRemainingAir(int airTicksLeft);

	public Object getMetaEntityCustomName(String name);

	public Object getMetaEntityProperties(boolean onFire, boolean crouched, boolean sprinting, boolean swimming, boolean invisible, boolean glowing, boolean flyingElytra);

	public Object getMetaEntityGravity(boolean gravity);

	public Object getMetaEntitySilenced(boolean silenced);

	public Object getMetaArmorStandProperties(boolean isSmall, boolean hasArmos, boolean noBasePlate, boolean marker);

	public Object getMetaEntityCustomNameVisible(boolean visible);

	public void scroll(Player sender, int previous);

	public int getAction(Object packetIn);

	public Vector getDirection(Object packet);

	public void spawnFallingBlock(int eid, UUID id, Location l, Player player, MaterialBlock mb);

	public void removeEntity(int eid, Player p);

	public void moveEntityRelative(int eid, Player p, double x, double y, double z, boolean onGround);

	public void teleportEntity(int eid, Player p, Location l, boolean onGround);

	public void spawnArmorStand(int eid, UUID id, Location l, int data, Player player);

	public void sendTeam(Player p, String id, String name, String prefix, String suffix, C color, int mode);

	public void addTeam(Player p, String id, String name, String prefix, String suffix, C color);

	public void updateTeam(Player p, String id, String name, String prefix, String suffix, C color);

	public void addToTeam(Player p, String id, String... entities);

	public void removeTeam(Player p, String id);

	public void removeFromTeam(Player p, String id, String... entities);

	public void displayScoreboard(Player p, int slot, String id);

	public void displayScoreboard(Player p, C c, String id);

	public void sendNewObjective(Player p, String id, String name);

	public void sendDeleteObjective(Player p, String id);

	public void sendEditObjective(Player p, String id, String name);

	public void sendScoreUpdate(Player p, String name, String objective, int score);

	public void sendScoreRemove(Player p, String name, String objective);

	/**
	 * Send remove glowing color data fro mthe entity
	 *
	 * @param p
	 *            the viewer
	 * @param glowing
	 *            the entity
	 */
	public void sendRemoveGlowingColorMeta(Player p, Entity glowing);

	/**
	 * Send a remove glowing color meta entity by uuid
	 *
	 * @param p
	 *            the player
	 * @param glowing
	 *            the glowing uuid
	 */
	public void sendRemoveGlowingColorMetaEntity(Player p, UUID glowing);

	/**
	 * Send glow removal
	 *
	 * @param p
	 *            the player
	 * @param glowing
	 *            the entity id
	 * @param name
	 *            the name
	 */
	public void sendRemoveGlowingColorMetaPlayer(Player p, UUID glowing, String name);

	/**
	 * Send glowing meta
	 *
	 * @param p
	 *            the player
	 * @param glowing
	 *            the glower
	 * @param color
	 *            the color
	 */
	public void sendGlowingColorMeta(Player p, Entity glowing, C color);

	/**
	 * Send glowing meta
	 *
	 * @param p
	 *            the player
	 * @param euid
	 *            the glower
	 * @param color
	 *            the color
	 */
	public void sendGlowingColorMetaEntity(Player p, UUID euid, C color);

	/**
	 * Send glowing meta
	 *
	 * @param p
	 *            the player
	 * @param euid
	 *            the glower
	 * @param color
	 *            the color
	 */
	public void sendGlowingColorMetaName(Player p, String euid, C color);

	/**
	 * Update the passengers for the given player
	 *
	 * @param p
	 *            the player
	 * @param vehicle
	 *            the vehicle
	 * @param passengers
	 *            the passenger entity ids
	 */
	public void updatePassengers(Player p, int vehicle, int... passengers);

	/**
	 * Send an advancement like a notification
	 *
	 * @param p
	 *            the player
	 * @param type
	 *            the frame type of the notification
	 * @param is
	 *            the item stack item to show
	 * @param text
	 *            the text to display
	 */
	public void sendAdvancement(Player p, FrameType type, ItemStack is, String text);

	/**
	 * Obtain a modifiable copy of a chunk in which light, blocks, and biomes can be
	 * set. You cannot set skylight if the given chunks world does not actually have
	 * a sky. Modifications are only tracked internally. A shadow copy is meant for
	 * converting into packets.
	 *
	 * Each time you send packets, there is no need to re-copy a shadow chunk.
	 * Simply use rebase() to re-copy from the real chunk. Any sent packets clear
	 * the modified mask too.
	 *
	 * @param at
	 *            the chunk to shadow copy
	 * @return the shadow copy
	 */
	public ShadowChunk shadowCopy(Chunk at);

	/**
	 * Constructs a full chunk send packet based off the world, refreshing it if
	 * there are modifications on the client's end of the chunk.
	 *
	 * @param chunk
	 *            the chunk
	 * @return the packet
	 */
	public Object packetChunkFullSend(Chunk chunk);

	/**
	 * Send chunk unload
	 *
	 * @param x
	 *            the x
	 * @param z
	 *            the z
	 * @return the packet
	 */
	public Object packetChunkUnload(int x, int z);

	/**
	 * Send a block change packet
	 *
	 * @param block
	 *            the block
	 * @param blockId
	 *            the block id
	 * @param blockData
	 *            the block data
	 * @return the packet
	 */
	public Object packetBlockChange(Location block, int blockId, byte blockData);

	/**
	 * Send a block action packet https://wiki.vg/Block_Actions
	 *
	 * @param block
	 *            the block
	 * @param action
	 *            the action type
	 * @param param
	 *            the action parameter
	 * @param blocktype
	 *            the block type
	 * @return the packet
	 */
	public Object packetBlockAction(Location block, int action, int param, int blocktype);

	/**
	 * Send an animation packet as an entity animating
	 * https://wiki.vg/index.php?title=Protocol&oldid=14204#Animation_.28clientbound.29
	 *
	 * @param eid
	 *            the entity id
	 * @param animation
	 *            the animation type
	 * @return the packet
	 */
	public Object packetAnimation(int eid, int animation);

	/**
	 * Send a block break animation packet. Each entity id can dig another block
	 * crack at the same time, works like keys.
	 *
	 * @param eid
	 *            the entity breaking the block
	 * @param location
	 *            the location
	 * @param damage
	 *            the damage 0-9
	 * @return the packet
	 */
	public Object packetBlockBreakAnimation(int eid, Location location, byte damage);

	/**
	 * Change the game state
	 * https://wiki.vg/index.php?title=Protocol&oldid=14204#Change_Game_State
	 *
	 * @param mode
	 *            the mode
	 * @param value
	 *            the value
	 * @return the packet
	 */
	public Object packetGameState(int mode, float value);

	/**
	 * Send a title message. You must send the packetTitleTimes after this to
	 * display it.
	 *
	 * @param title
	 *            the title message
	 * @return the packet
	 */
	public Object packetTitleMessage(String title);

	/**
	 * Send a subtitle message. You must send the packetTitleTimes after this to
	 * display it. Subtitles will not display unless a title packet is also sent
	 * before the times too.
	 *
	 * @param subtitle
	 *            the subtitle message.
	 * @return the packet
	 */
	public Object packetSubtitleMessage(String subtitle);

	/**
	 * Send an action bar message
	 *
	 * @param actionmsg
	 *            the action message
	 * @return the packet
	 */
	public Object packetActionBarMessage(String actionmsg);

	/**
	 * Send a title reset packet (times)
	 *
	 * @return the packet
	 */
	public Object packetResetTitle();

	/**
	 * Send a clear title packet
	 *
	 * @return the packet
	 */
	public Object packetClearTitle();

	/**
	 * Sending packet times displays the last sent title and subtitle. You must send
	 * either the title only, or the title and subtitle first before sending the
	 * times.
	 *
	 * @param in
	 *            the fade in time
	 * @param stay
	 *            the stay time
	 * @param out
	 *            the fade out time
	 * @return the packet
	 */
	public Object packetTimes(int in, int stay, int out);

	/**
	 * Get latest settings for the given player
	 *
	 * @param p
	 *            the player
	 * @return the player settings
	 */
	public PlayerSettings getSettings(Player p);

	/**
	 * Send a packet to the specified player
	 *
	 * @param p
	 *            the player
	 * @param o
	 *            the packet
	 */
	public void sendPacket(Player p, Object o);

	/**
	 * Send a packet to all players within a radius from the specified location
	 *
	 * @param radius
	 *            the radius
	 * @param l
	 *            the location
	 * @param o
	 *            the packet
	 */
	public void sendRangedPacket(double radius, Location l, Object o);

	/**
	 * Send a packet to all players in the specified world
	 *
	 * @param w
	 *            the world
	 * @param o
	 *            the packet
	 */
	public void sendGlobalPacket(World w, Object o);

	/**
	 * Send a packet to all players on the server
	 *
	 * @param o
	 *            the packet
	 */
	public void sendUniversalPacket(Object o);

	/**
	 * Send a packet to all players which have a view distance (captured from client
	 * settings) that can "observe" the specified chunk. This is very useful for
	 * avoiding the player's empty chunk cache, when setting a block outside of
	 * their view distance, or in an unloaded chunk.
	 *
	 * @param c
	 *            the chunk
	 * @param o
	 *            the packet
	 */
	public void sendViewDistancedPacket(Chunk c, Object o);

	/**
	 * Checks if the specified player can see the given chunk
	 *
	 * @param c
	 *            the chunk
	 * @param p
	 *            the player
	 * @return true if the player can
	 */
	public boolean canSee(Chunk c, Player p);

	/**
	 * Checks if the specified player can see the chunk of the given location
	 *
	 * @param l
	 *            the location
	 * @param p
	 *            the player
	 * @return true if the player can
	 */
	public boolean canSee(Location l, Player p);

	/**
	 * Returns the view distance of the given player (maxed by the server's view
	 * distance)
	 *
	 * @param p
	 *            the player
	 * @return the view distance sent from the player client
	 */
	public int getViewDistance(Player p);

	/**
	 * List all of the players that has the specified chunk in their visible view
	 * distnace
	 *
	 * @param c
	 *            the chunk
	 * @return the players
	 */
	public List<Player> getObservers(Chunk c);

	/**
	 * List all of the players that has the specified location's chunk in their
	 * visible view distnace
	 *
	 * @param c
	 *            the chunk
	 * @return the players
	 */
	public List<Player> getObservers(Location l);

	/**
	 * Get the server NMS package version e.g 1_12_R1
	 *
	 * @return the version
	 */
	public String getServerVersion();

	/**
	 * Get a friendly readable version indicator for the intended server/client
	 * version such as 1.12.X
	 *
	 * @return the version
	 */
	public String getVersion();

	/**
	 * Start the handler
	 */
	public void start();

	/**
	 * Stop the handler
	 */
	public void stop();

	public Set<Object> getTickList(World world);

	public Set<Object> getTickListFluid(World world);

	public Block getBlock(World world, Object tickListEntry);

	public Object packetTabHeaderFooter(String h, String f);
}
