package primal.bukkit.nms;

/**
 * Hosts a packet listener
 *
 * @author cyberpwn
 *
 */
public interface PacketListener
{
	/**
	 * Called when the packet listener is opened
	 */
    void onOpened();

	/**
	 * Start listening for packets
	 */
    void openListener();

	/**
	 * Close the packet listener
	 */
    void closeListener();

	/**
	 * Add a packet listener for outgoing packets (server -> client)
	 *
	 * @param packetType
	 *            the packet type
	 * @param handler
	 *            the handler. The return value denotes weather or not to send the
	 *            packet to the player or not. Returning true sends the packet,
	 *            false cancels it.
	 */
    <T> void addOutgoingListener(Class<? extends T> packetType, PacketHandler<T> handler);

	/**
	 * Add a packet listener for outgoing packets (server -> client)
	 *
	 * @param handler
	 *            the handler. The return value denotes weather or not to send the
	 *            packet to the player or not. Returning true sends the packet,
	 *            false cancels it.
	 */
    void addGlobalOutgoingListener(PacketHandler<?> handler);

	/**
	 * Add a packet listener for incoming packets (client -> server)
	 *
	 * @param packetType
	 *            the packet type
	 * @param handler
	 *            the handler. The return value denotes weather or not to send the
	 *            packet to the server (handled by craftbukkit) or not. Returning
	 *            true sends the packet, false cancels it.
	 */
    <T> void addIncomingListener(Class<? extends T> packetType, PacketHandler<T> handler);

	/**
	 * Add a packet listener for incoming packets (client -> server)
	 *
	 * @param handler
	 *            the handler. The return value denotes weather or not to send the
	 *            packet to the server (handled by craftbukkit) or not. Returning
	 *            true sends the packet, false cancels it.
	 */
    void addGlobalIncomingListener(PacketHandler<?> handler);

	/**
	 * Remove all outgoing listeners for the given packet type
	 *
	 * @param c
	 *            the packet class
	 */
    void removeOutgoingPacketListeners(Class<?> c);

	/**
	 * Remove all outgoing packet listeners
	 */
    void removeOutgoingPacketListeners();

	/**
	 * Remove all incoming listeners for the given packet type
	 *
	 * @param c
	 *            the packet class
	 */
    void removeIncomingPacketListeners(Class<?> c);

	/**
	 * Remove all incoming packet listeners
	 */
    void removeIncomingPacketListeners();
}
