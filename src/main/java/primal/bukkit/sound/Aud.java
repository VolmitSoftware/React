package primal.bukkit.sound;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Audio sounds commonly used
 *
 * @author cyberpwn
 */
public enum Aud
{
	CLICK(new GSound(Sound.UI_BUTTON_CLICK, 1f, 1.5f));

	private Audible aud;

	private Aud(Audible aud)
	{
		this.aud = aud;
	}

	public Audible get()
	{
		return aud;
	}

	public void play(Player p)
	{
		get().play(p);
	}

	public void play(Location l)
	{
		get().play(l);
	}
}
