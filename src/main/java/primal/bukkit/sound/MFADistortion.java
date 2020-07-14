package primal.bukkit.sound;

public class MFADistortion extends AudioDistortion
{
	private int span;
	private float to;
	
	public MFADistortion(int span, float to)
	{
		this.span = span;
		this.to = to;
	}
	
	@Override
	public Audible distort(Audible a)
	{
		Audio n = new Audio();
		
		float start = a.getPitch();
		
		if(start > to)
		{
			float st = start;
			start = to;
			to = st;
			a.setPitch(start);
		}
		
		for(int i = 0; i < span; i++)
		{
			Float diff = i * ((to - start) / span);
			Audible ab = a.clone();
			ab.setPitch(start + diff);
			n.add(ab);
		}
		
		return n;
	}
}
