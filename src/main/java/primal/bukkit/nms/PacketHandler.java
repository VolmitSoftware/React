package primal.bukkit.nms;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface PacketHandler<T extends Object>
{
	/**
	 * Called when a packet was received either from the player or from the server
	 * to the player. The return result is the modified packet, return null to
	 * cancel it.
	 *
	 * @param player
	 *            the player
	 * @param packet
	 *            the packet
	 * @return the packet to actually send
	 */
	public Object onPacket(Player player, Object packet);
}
