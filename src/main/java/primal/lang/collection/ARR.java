package primal.lang.collection;

/**
 * Array utilities
 *
 * @author cyberpwn
 */
public class ARR
{
	/**
	 * Create a sized object array
	 *
	 * @param size
	 *            the size of the array
	 * @return an object array of the specified size
	 */
	public static Object[] create(int size)
	{
		return new Object[size];
	}

	/**
	 * Count objects in the indexes which are not null
	 *
	 * @param arr
	 *            the array
	 * @return the number of elements not null in the array
	 */
	public static int countUsed(Object[] arr)
	{
		return arr.length - countFree(arr);
	}

	/**
	 * Count objects in the indexes which are null
	 *
	 * @param arr
	 *            the array
	 * @return the number of elements null in the array
	 */
	public static int countFree(Object[] arr)
	{
		int k = 0;

		for(int i = 0; i < arr.length; i++)
		{
			if(arr[i] == null)
			{
				k++;
			}
		}

		return k;
	}

	/**
	 * Pushes all elements in the array as far as they can go to the least index
	 * possible treating null as empty object space in the array. This retains
	 * order and literal size of the array. Only permutations are used to move
	 * elements around the array.
	 *
	 * @param arr
	 *            the array to defrag
	 */
	public static void defrag(Object[] arr)
	{
		for(int i = 1; i < arr.length; i++)
		{
			if(arr[i] != null)
			{
				int k = -1;

				for(int j = 0; j < i; j++)
				{
					if(arr[j] == null)
					{
						k = j;
						break;
					}
				}

				if(k >= 0)
				{
					arr[k] = arr[i];
					arr[i] = null;
				}
			}
		}
	}
}
