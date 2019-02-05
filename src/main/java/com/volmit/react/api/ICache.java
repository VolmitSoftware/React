package com.volmit.react.api;

import primal.lang.collection.GList;
import primal.lang.collection.GSet;

public interface ICache<K, V>
{
	public GSet<V> get(K k);

	public void put(K k, V v);

	public void clear(K k);

	public void clear();

	public boolean has(K k);

	public GList<K> k();
}
