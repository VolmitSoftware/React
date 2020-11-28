package primal.lang.collection;

public class NetCache<K, V>
{
	private final Resolver<K, V> resolver;
	private final GMap<K, V> cache;

	public NetCache(Resolver<K, V> resolver)
	{
		cache = new GMap<K, V>();
		this.resolver = resolver;
	}

	public GMap<K, V> cache()
	{
		return cache;
	}

	public void clear()
	{
		cache.clear();
	}

	public GList<K> k()
	{
		return cache.k();
	}

	public void put(K k, V v)
	{
		cache.put(k, v);
	}

	public boolean has(K k)
	{
		return cache.containsKey(k);
	}

	public void invalidate(K k)
	{
		cache.remove(k);
	}

	public V getReal(K k)
	{
		invalidate(k);
		return get(k);
	}

	public V get(K k)
	{
		if(!has(k))
		{
			put(k, resolver.resolve(k));
		}

		return cache.get(k);
	}
}
