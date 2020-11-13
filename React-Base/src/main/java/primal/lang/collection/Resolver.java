package primal.lang.collection;

@FunctionalInterface
public interface Resolver<K, V>
{
	V resolve(K k);
}
