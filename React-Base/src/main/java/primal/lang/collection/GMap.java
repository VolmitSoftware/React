package primal.lang.collection;


import java.util.HashMap;
import java.util.Map;

public class GMap<K, V> extends HashMap<K, V>
{
	private static final long serialVersionUID = 1527847670799761130L;

	public GMap()
	{
		super();
	}

	public GMap(Map<K, V> map)
	{
		super();

		for(K i : map.keySet())
		{
			put(i, map.get(i));
		}
	}

	/**
	 * Copy the map
	 *
	 * @return the copied map
	 */
	public GMap<K, V> copy()
	{
		GMap<K, V> m = new GMap<K, V>();

		for(K k : this.keySet())
		{
			m.put(k, get(k));
		}

		return m;
	}

	/**
	 * Chain put
	 *
	 * @param k
	 *            the key
	 * @param v
	 *            the value
	 * @return the modified map
	 */
	public GMap<K, V> qput(K k, V v)
	{
		put(k, v);
		return this.copy();
	}

	/**
	 * Flips the maps keys and values.
	 *
	 * @return GMap V, K instead of K, V
	 */
	public GMap<V, GList<K>> flip()
	{
		GMap<V, GList<K>> flipped = new GMap<V, GList<K>>();

		for(K i : keySet())
		{
			if(i == null)
			{
				continue;
			}

			if(!flipped.containsKey(get(i)))
			{
				flipped.put(get(i), new GList<K>());
			}

			flipped.get(get(i)).add(i);
		}

		return flipped;
	}

	@Override
	public String toString()
	{
		GList<String> s = new GList<String>();

		for(K i : keySet())
		{
			s.add(i.toString() + ": " + get(i).toString());
		}

		return "[" + s.toString() + "]";
	}

	/**
	 * Add maps contents into the current map
	 *
	 * @param umap
	 *            the map to add in
	 * @return the modified current map
	 */
	public GMap<K, V> append(GMap<K, V> umap)
	{
		for(K i : umap.keySet())
		{
			put(i, umap.get(i));
		}

		return this;
	}

	/**
	 * Get a copied GList of the keys (modification safe)
	 *
	 * @return keys
	 */
	public GList<K> k()
	{
		return new GList<K>(keySet());
	}

	/**
	 * Get a copied GSet of the keys (modification safe)
	 *
	 * @return keys
	 */
	public GSet<K> kset()
	{
		return new GSet<K>(keySet());
	}

	/**
	 * Get a copied GList of the values (modification safe)
	 *
	 * @return values
	 */
	public GList<V> v()
	{
		return new GList<V>(values());
	}

	/**
	 * Get a copied GSet of the values (modification safe)
	 *
	 * @return values
	 */
	public GSet<V> vset()
	{
		return new GSet<V>(values());
	}

	/**
	 * Put if and only if the key does not yet exist
	 *
	 * @param k
	 *            the key
	 * @param v
	 *            the value
	 */
	public void putNVD(K k, V v)
	{
		if(!containsValue(v))
		{
			put(k, v);
		}
	}

	/**
	 * Get a Glist of values from a list of keys
	 *
	 * @param keys
	 *            the requested keys
	 * @return the resulted values
	 */
	public GList<V> get(GList<K> keys)
	{
		GList<V> ulv = new GList<V>();

		for(K i : keys)
		{
			if(get(i) != null)
			{
				ulv.add(get(i));
			}
		}

		return ulv;
	}

	/**
	 * Removes duplicate values by removing keys with values that match other values
	 * with different keys
	 *
	 * @return the modified map
	 */
	public GMap<K, V> removeDuplicateValues()
	{
		GMap<K, V> map = this.copy();
		GList<K> keys = map.k().removeDuplicates();

		clear();

		for(K i : keys)
		{
			put(i, map.get(i));
		}

		return this;
	}

	/**
	 * Put a bunch of keys and values does nothing if the lists sizes dont match.
	 * The order of the list is how assignment will be determined
	 *
	 * @param k
	 *            the keys
	 * @param v
	 *            the values
	 */
	public void put(GList<K> k, GList<V> v)
	{
		if(k.size() != v.size())
		{
			return;
		}

		for(int i = 0; i < k.size(); i++)
		{
			put(k, v);
		}
	}

	/**
	 * Sort keys based on values sorted
	 *
	 * @return the sorted keys
	 */
	public GList<K> sortK()
	{
		GList<K> k = new GList<K>();
		GList<V> v = v();

		v.sort();

		for(V i : v)
		{
			for(K j : k())
			{
				if(get(j).equals(i))
				{
					k.add(j);
				}
			}
		}

		return k;
	}

	/**
	 * Sort values based on keys sorted
	 *
	 * @return the sorted values
	 */
	public GList<V> sortV()
	{
		GList<V> v = new GList<V>();
		GList<K> k = k();

		k.sort();

		for(K i : k)
		{
			for(V j : v())
			{
				if(get(i).equals(j))
				{
					v.add(j);
				}
			}
		}

		return v;
	}
}
