package primal.bukkit.particle;
import org.bukkit.Color;
import org.bukkit.Location;

import primal.lang.collection.GList;

public class ColoredParticleEffect implements VisualEffect
{
	private Color color;
	private boolean alpha;

	public ColoredParticleEffect(Color color)
	{
		this.color = color;
		alpha = false;
	}

	@Override
	public GList<VisualEffect> getEffects()
	{
		return new GList<VisualEffect>();
	}

	@Override
	public void play(Location l)
	{
		ParticleEffect.OrdinaryColor oc = new ParticleEffect.OrdinaryColor(color.getRed(), color.getGreen(), color.getBlue());

		if(alpha)
		{
			ParticleEffect.SPELL_MOB_AMBIENT.display(oc, l, 40);
		}

		else
		{
			ParticleEffect.SPELL_MOB.display(oc, l, 40);
		}
	}

	@Override
	public void addEffect(VisualEffect e)
	{

	}

	public Color getColor()
	{
		return color;
	}

	public void setColor(Color color)
	{
		this.color = color;
	}

	public boolean isAlpha()
	{
		return alpha;
	}

	public void setAlpha(boolean alpha)
	{
		this.alpha = alpha;
	}
}