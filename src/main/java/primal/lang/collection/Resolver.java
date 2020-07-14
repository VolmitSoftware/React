package primal.lang.collection;

@FunctionalInterface
public interface Resolver<K, V>
{
	public V resolve(K k);
}
