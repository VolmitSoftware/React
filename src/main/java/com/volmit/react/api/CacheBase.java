package com.volmit.react.api;

import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GMap;
import com.volmit.volume.lang.collections.GSet;

public class CacheBase<K, V> implements ICache<K, V>
{
	private GMap<K, GSet<V>> cache;

	public CacheBase()
	{
		cache = new GMap<K, GSet<V>>();
	}

	@Override
	public GSet<V> get(K k)
	{
		return cache.get(k);
	}

	@Override
	public void put(K k, V v)
	{
		if(!cache.containsKey(k))
		{
			cache.put(k, new GSet<V>());
		}

		cache.get(k).add(v);
	}

	@Override
	public void clear(K k)
	{
		cache.remove(k);
	}

	@Override
	public void clear()
	{
		cache.clear();
	}

	@Override
	public GList<K> k()
	{
		return cache.k();
	}

	@Override
	public boolean has(K k)
	{
		return cache.containsKey(k);
	}
}
