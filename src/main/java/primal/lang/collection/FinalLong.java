package primal.lang.collection;

/**
 * Represents a number that can be finalized and be changed
 *
 * @author cyberpwn
 */
public class FinalLong extends Wrapper<Long>
{
	public FinalLong(Long t)
	{
		super(t);
	}

	/**
	 * Add to this value
	 *
	 * @param i
	 *            the number to add to this value (value = value + i)
	 */
	public void add(long i)
	{
		set(get() + i);
	}

	/**
	 * Subtract from this value
	 *
	 * @param i
	 *            the number to subtract from this value (value = value - i)
	 */
	public void sub(long i)
	{
		set(get() - i);
	}
}
