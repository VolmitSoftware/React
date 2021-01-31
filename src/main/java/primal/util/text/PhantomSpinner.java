package primal.util.text;
/**
 * Colored circle spinner
 *
 * @author cyberpwn
 */
public class PhantomSpinner
{
	private final ProgressSpinner s;
	private final ProgressSpinner c;

	public PhantomSpinner()
	{
		s = new ProgressSpinner();
		c = new ProgressSpinner(C.LIGHT_PURPLE.toString(), C.LIGHT_PURPLE.toString(), C.LIGHT_PURPLE.toString(), C.DARK_PURPLE.toString(), C.DARK_GRAY.toString(), C.DARK_GRAY.toString(), C.DARK_GRAY.toString(), C.DARK_PURPLE.toString());
	}

	@Override
	public String toString()
	{
		return c.toString() + s.toString();
	}
}