package primal.logic.format;

public class Flagger
{
	private String prefix;
	private String split;
	private String[] args;
	
	public Flagger(String prefix, String split, String[] args)
	{
		this.prefix = prefix;
		this.split = split;
		this.args = args;
	}
	
	public Flagger(String[] args)
	{
		this("-", ":", args);
	}
	
	public boolean hasFlag(String flag)
	{
		for(String i : args)
		{
			if(i.equals(prefix + flag))
			{
				return true;
			}
			
			if(i.startsWith(prefix + flag + split))
			{
				return true;
			}
		}
		
		return false;
	}
	
	public boolean hasContent(String flag)
	{
		for(String i : args)
		{
			if(i.startsWith(prefix + flag + split))
			{
				return true;
			}
		}
		
		return false;
	}
	
	public String getContent(String flag)
	{
		for(String i : args)
		{
			if(i.startsWith(prefix + flag + split))
			{
				return i.split(":")[1];
			}
		}
		
		return null;
	}
}
