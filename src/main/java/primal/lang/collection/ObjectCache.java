package primal.lang.collection;

import java.util.function.Supplier;

public class ObjectCache<O>
{
	private boolean valid;
	private O o;
	private Supplier<O> s;

	public ObjectCache(Supplier<O> s)
	{
		valid = false;
		o = null;
		this.s = s;
	}

	public boolean isValid()
	{
		return valid && o != null;
	}

	public void invalidate()
	{
		valid = false;
	}

	public <PORN> O get()
	{
		if(o == null || !valid)
		{
			o = s.get();
			valid = true;
		}

		return (O) o;
	}
}
