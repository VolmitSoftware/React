package primal.lang.collection;

import java.util.Collection;
import java.util.HashSet;

public class GSet<T> extends HashSet<T>
{
	private static final long serialVersionUID = 1L;

	public GSet()
	{
		super();
	}

	public GSet(Collection<? extends T> c)
	{
		super(c);
	}

	public GSet(int initialCapacity, float loadFactor)
	{
		super(initialCapacity, loadFactor);
	}

	public GSet(int initialCapacity)
	{
		super(initialCapacity);
	}
}
