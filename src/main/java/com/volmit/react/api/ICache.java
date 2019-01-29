package com.volmit.react.api;

import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GSet;

public interface ICache<K, V>
{
	public GSet<V> get(K k);

	public void put(K k, V v);

	public void clear(K k);

	public void clear();

	public boolean has(K k);

	public GList<K> k();
}
