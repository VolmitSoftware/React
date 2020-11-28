package primal.lang.collection;

/**
 * Represents a number that can be finalized and be changed
 *
 * @author cyberpwn
 */
public class FinalBoolean extends Wrapper<Boolean>
{
	public FinalBoolean(Boolean t)
	{
		super(t);
	}

	public void toggle()
	{
		set(!get());
	}
}
