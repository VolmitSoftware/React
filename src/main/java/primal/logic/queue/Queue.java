package primal.logic.queue;

import primal.lang.collection.GList;

public interface Queue<T>
{
	void queue(T t);

	void queue(GList<T> t);

	boolean hasNext(int amt);

	boolean hasNext();

	T next();

	GList<T> next(int amt);

	void clear();

	int size();
}
