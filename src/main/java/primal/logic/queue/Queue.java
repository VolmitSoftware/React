package primal.logic.queue;

import primal.lang.collection.GList;

public interface Queue<T>
{
	public void queue(T t);

	public void queue(GList<T> t);

	public boolean hasNext(int amt);

	public boolean hasNext();

	public T next();

	public GList<T> next(int amt);

	public void clear();

	public int size();
}
