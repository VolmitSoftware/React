package primal.bukkit.world;

import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Fast potion effects like this
 * <br/>
 * <br/>
 * PE.HEAL.d(40).a(2).c(); >> PotionEffect (a = amp; d = dur; c = create)
 * <br/>
 * PE.HEAL.d(40).a(2).c(Player p) >> Apply potion effect;
 *
 * @author cyberpwn
 */
public class PE extends PotionEffect
{
	public static PE ABSORPTION;
	public static PE BLINDNESS;
	public static PE CONFUSION;
	public static PE DAMAGE_RESISTANCE;
	public static PE FAST_DIGGING;
	public static PE FIRE_RESISTANCE;
	public static PE GLOWING;
	public static PE HARM;
	public static PE HEAL;
	public static PE HEALTH_BOOST;
	public static PE HUNGER;
	public static PE INCREASE_DAMAGE;
	public static PE INVISIBILITY;
	public static PE JUMP;
	public static PE LEVITATION;
	public static PE LUCK;
	public static PE NIGHT_VISION;
	public static PE POISON;
	public static PE REGENERATION;
	public static PE SATURATION;
	public static PE SLOW;
	public static PE SLOW_DIGGING;
	public static PE SPEED;
	public static PE UNLUCK;
	public static PE WATER_BREATHING;
	public static PE WEAKNESS;
	public static PE WITHER;

	public PE(Map<String, Object> map)
	{
		super(map);
	}

	public PE(PotionEffectType type, int duration, int amplifier, boolean ambient, boolean particles)
	{
		super(type, duration, amplifier, ambient, particles);
	}

	public PE(PotionEffectType type, int duration, int amplifier, boolean ambient)
	{
		super(type, duration, amplifier, ambient);
	}

	public PE(PotionEffectType type, int duration, int amplifier)
	{
		super(type, duration, amplifier);
	}

	@Override
	public Map<String, Object> serialize()
	{
		return super.serialize();
	}

	@Override
	public boolean apply(LivingEntity entity)
	{
		return super.apply(entity);
	}

	@Override
	public boolean equals(Object obj)
	{
		return super.equals(obj);
	}

	@Override
	public int getAmplifier()
	{
		return super.getAmplifier();
	}

	@Override
	public int getDuration()
	{
		return super.getDuration();
	}

	@Override
	public PotionEffectType getType()
	{
		return super.getType();
	}

	@Override
	public boolean isAmbient()
	{
		return super.isAmbient();
	}

	@Override
	public boolean hasParticles()
	{
		return super.hasParticles();
	}

	@Override
	public int hashCode()
	{
		return super.hashCode();
	}

	@Override
	public String toString()
	{
		return super.toString();
	}

	public static PE defaulted(PotionEffectType type)
	{
		return new PE(type, 1, 0, false, false);
	}

	/**
	 * Set the potion duration
	 *
	 * @param duration
	 *            the duration
	 * @return the new potion wrapper
	 */
	public PE d(int duration)
	{
		return new PE(getType(), duration, getAmplifier(), false, false);
	}

	/**
	 * Set the potion amp
	 *
	 * @param amp
	 *            the amp
	 * @return the new potion wrapper
	 */
	public PE a(int amp)
	{
		return new PE(getType(), getDuration(), amp, false, false);
	}

	/**
	 * Create the effect
	 *
	 * @return the potion effect
	 */
	public PotionEffect c()
	{
		return new PE(getType(), getDuration(), getAmplifier(), false, false);
	}

	/**
	 * Apply an effect to a player/entity
	 *
	 * @param l
	 *            the living entity
	 */
	public void c(LivingEntity l)
	{
		l.addPotionEffect(new PE(getType(), getDuration(), getAmplifier(), false, false).c());
	}

	public void crm(LivingEntity l)
	{
		l.addPotionEffect(new PE(getType(), getDuration(), getAmplifier(), false, false).c(), true);
	}

	public void rm(LivingEntity l)
	{
		l.removePotionEffect(getType());
	}

	static
	{
		ABSORPTION = defaulted(PotionEffectType.ABSORPTION);
		BLINDNESS = defaulted(PotionEffectType.BLINDNESS);
		CONFUSION = defaulted(PotionEffectType.CONFUSION);
		DAMAGE_RESISTANCE = defaulted(PotionEffectType.DAMAGE_RESISTANCE);
		FAST_DIGGING = defaulted(PotionEffectType.FAST_DIGGING);
		FIRE_RESISTANCE = defaulted(PotionEffectType.FIRE_RESISTANCE);
		GLOWING = defaulted(PotionEffectType.GLOWING);
		HARM = defaulted(PotionEffectType.HARM);
		HEAL = defaulted(PotionEffectType.HEAL);
		HEALTH_BOOST = defaulted(PotionEffectType.HEALTH_BOOST);
		HUNGER = defaulted(PotionEffectType.HUNGER);
		INCREASE_DAMAGE = defaulted(PotionEffectType.INCREASE_DAMAGE);
		INVISIBILITY = defaulted(PotionEffectType.INVISIBILITY);
		JUMP = defaulted(PotionEffectType.JUMP);
		LEVITATION = defaulted(PotionEffectType.LEVITATION);
		LUCK = defaulted(PotionEffectType.LUCK);
		NIGHT_VISION = defaulted(PotionEffectType.NIGHT_VISION);
		POISON = defaulted(PotionEffectType.POISON);
		REGENERATION = defaulted(PotionEffectType.REGENERATION);
		SATURATION = defaulted(PotionEffectType.SATURATION);
		SLOW = defaulted(PotionEffectType.SLOW);
		SLOW_DIGGING = defaulted(PotionEffectType.SLOW_DIGGING);
		SPEED = defaulted(PotionEffectType.SPEED);
		UNLUCK = defaulted(PotionEffectType.UNLUCK);
		WATER_BREATHING = defaulted(PotionEffectType.WATER_BREATHING);
		WEAKNESS = defaulted(PotionEffectType.WEAKNESS);
		WITHER = defaulted(PotionEffectType.WITHER);
	}
}