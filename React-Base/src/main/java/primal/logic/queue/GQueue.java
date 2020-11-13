package primal.logic.queue;

import primal.lang.collection.GList;

public class GQueue<T> implements Queue<T>
{
	private GList<T> queue;
	private boolean randomPop;
	private boolean reversePop;

	public GQueue()
	{
		clear();
	}

	public GQueue<T> responsiveMode()
	{
		reversePop = true;
		return this;
	}

	public GQueue<T> randomMode()
	{
		randomPop = true;
		return this;
	}

	public void queue(T t)
	{
		queue.add(t);
	}

	public void queue(GList<T> t)
	{
		for(T i : t)
		{
			queue(i);
		}
	}

	public boolean hasNext(int amt)
	{
		return queue.size() >= amt;
	}

	public boolean hasNext()
	{
		return !queue.isEmpty();
	}

	public T next()
	{
		return reversePop ? queue.popLast() : randomPop ? queue.popRandom() : queue.pop();
	}

	public GList<T> next(int amt)
	{
		GList<T> t = new GList<T>();

		for(int i = 0; i < amt; i++)
		{
			if(!hasNext())
			{
				break;
			}

			t.add(next());
		}

		return t;
	}

	public void clear()
	{
		queue = new GList<T>();
	}

	public int size()
	{
		return queue.size();
	}
}
