package primal.bukkit.sound;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import primal.bukkit.sched.SR;
import primal.bukkit.world.Area;

/**
 * Audio bound to entities
 *
 * @author cyberpwn
 *
 */
public class AudibleEntity
{
	private Entity entity;
	private Audible audible;
	private SR task;

	/**
	 * Create the audio bind to an entity
	 *
	 * @param entity
	 *            the entity
	 * @param audible
	 *            the audio
	 * @param interval
	 *            the interval in ticks to play
	 */
	public AudibleEntity(Entity entity, Audible audible, Integer interval)
	{
		this.entity = entity;
		this.audible = audible;
		this.task = null;

		if(interval > -1)
		{
			task = new SR(interval)
			{
				@Override
				public void run()
				{
					if(entity == null || entity.isDead())
					{
						cancel();
						return;
					}

					Area a = new Area(entity.getLocation(), 64);

					for(Player i : a.getNearbyPlayers())
					{
						onPlay(i, audible.clone(), entity);
					}
				}
			};
		}
	}

	/**
	 * Stop playing the sound
	 */
	public void cancel()
	{
		if(task != null)
		{
			task.cancel();
		}
	}

	/**
	 * Override when played
	 *
	 * @param i
	 *            the player
	 * @param audible
	 *            the sound
	 * @param entity
	 *            the entity
	 */
	protected void onPlay(Player i, Audible audible, Entity entity)
	{
		audible.play(i, entity.getLocation());
	}

	/**
	 * Get the audible sound
	 *
	 * @return the sound
	 */
	public Audible getAudible()
	{
		return audible;
	}

	/**
	 * Set the sound
	 *
	 * @param audible
	 *            the sound
	 */
	public void setAudible(Audible audible)
	{
		this.audible = audible;
	}

	/**
	 * Get the entity
	 *
	 * @return the entity
	 */
	public Entity getEntity()
	{
		return entity;
	}
}
