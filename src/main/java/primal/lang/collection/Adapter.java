package primal.lang.collection;


/**
 * An adapter converts one object into another
 *
 * @author cyberpwn
 * @param <FROM>
 *            the given object
 * @param <TO>
 *            the resulting object
 */
public interface Adapter<FROM, TO>
{
	/**
	 * Adapt an object
	 *
	 * @param from
	 *            the from object
	 * @return the adapted object
	 */
	public TO adapt(FROM from);

	/**
	 * Handles the adapter processing
	 *
	 * @param from
	 *            the from object
	 * @return the adapted object
	 */
	public TO onAdapt(FROM from);
}
