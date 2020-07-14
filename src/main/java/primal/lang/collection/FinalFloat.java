package primal.lang.collection;

/**
 * Represents a number that can be finalized and be changed
 *
 * @author cyberpwn
 */
public class FinalFloat extends Wrapper<Float>
{
	public FinalFloat(Float t)
	{
		super(t);
	}

	/**
	 * Add to this value
	 *
	 * @param i
	 *            the number to add to this value (value = value + i)
	 */
	public void add(float i)
	{
		set(get() + i);
	}

	/**
	 * Subtract from this value
	 *
	 * @param i
	 *            the number to subtract from this value (value = value - i)
	 */
	public void sub(float i)
	{
		set(get() - i);
	}
}
