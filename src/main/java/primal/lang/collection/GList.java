package primal.lang.collection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import primal.lang.json.JSONArray;

/**
 * GLists are Arraylists with special enhancements
 *
 * @author cyberpwn
 * @param <T>
 *            the type of list T
 */
public class GList<T> extends ArrayList<T>
{
	private static final long serialVersionUID = 4480457702775755227L;

	/**
	 * Create an empty GList
	 */
	public GList()
	{
		super();
	}

	public void addFirst(T t)
	{
		add(0, t);
	}

	public void addLast(T t)
	{
		add(t);
	}

	public GList<T> grepExplicit(int startIndex, int endIndex)
	{
		GList<T> f = new GList<T>();

		for(int i = startIndex; i < endIndex + 1; i++)
		{
			f.add(getAt(i));
		}

		return f;
	}

	public GList<T> grepDistance(int startIndex, int size)
	{
		GList<T> f = new GList<T>();

		for(int i = 0; i < size; i++)
		{
			f.add(getAt(i + startIndex));
		}

		return f;
	}

	public T getAt(int index)
	{
		return get((int) index);
	}

	public T getAt(Integer index)
	{
		return get(index.intValue());
	}

	/**
	 * Create a new GList from a Set of the same type
	 *
	 * @param set
	 *            the given set
	 */
	public GList(Set<T> set)
	{
		super();

		for(T i : set)
		{
			add(i);
		}
	}

	/**
	 * Get a GList<String> from a JSONArray
	 *
	 * @param ja
	 *            the JSONArray
	 * @return a GList<String> representing this json array
	 */
	public static GList<String> from(JSONArray ja)
	{
		GList<String> g = new GList<String>();

		for(int i = 0; i < ja.length(); i++)
		{
			g.add(ja.getString(i));
		}

		return g;
	}

	/**
	 * Create a new GList from a Collection of the same type
	 *
	 * @param set
	 *            the given collection
	 */
	public GList(Collection<T> set)
	{
		super();

		for(T i : set)
		{
			add(i);
		}
	}

	/**
	 * Create a glist by iterating through an iterator
	 *
	 * @param it
	 *            the iterator
	 */
	public GList(Iterator<T> it)
	{
		super();

		while(it.hasNext())
		{
			add(it.next());
		}
	}

	/**
	 * Create a GList with an array of the same type
	 *
	 * @param array
	 *            the array to start off this list
	 */
	public GList(T[] array)
	{
		super();
		add(array);
	}

	/**
	 * Create a GList with an existing list of the same type
	 *
	 * @param array
	 *            a list of the same type (essentially a clone) but from any type
	 *            implementing List<T>
	 */
	public GList(List<T> array)
	{
		super();

		if(array == null)
		{
			return;
		}

		add(array);
	}

	public JSONArray toJSONStringArray()
	{
		JSONArray j = new JSONArray();

		for(Object i : this)
		{
			j.put(i.toString());
		}

		return j;
	}

	/**
	 * Get the most common element in the list, may return any if no duplicates
	 *
	 * @return the most common element
	 */
	public T mostCommon()
	{
		GMap<T, Integer> common = new GMap<T, Integer>();
		Iterator<T> it = iterator();

		while(it.hasNext())
		{
			T i = it.next();

			if(!common.containsKey(i))
			{
				common.put(i, 0);
			}

			common.put(i, common.get(i) + 1);
		}

		int sm = 0;
		T v = null;

		for(T i : common.keySet())
		{
			if(common.get(i) > sm)
			{
				sm = common.get(i);
				v = i;
			}
		}

		return v;
	}

	/**
	 * Get a shuffled copy of this list. A COPY.
	 *
	 * @return a Glist of the same type as this, shuffled.
	 */
	public GList<T> shuffleCopy()
	{
		GList<T> o = copy();
		Collections.shuffle(o);
		return o;
	}

	/**
	 * Shuffle this list. (randomize)
	 */
	public void shuffle()
	{
		Collections.shuffle(this);
	}

	/**
	 * Split this list into multiple lists
	 *
	 * @param v
	 *            the divisor (how many lists will be returned)
	 * @return the list of lists
	 */
	public GList<GList<T>> split(int v)
	{
		GList<GList<T>> mtt = new GList<GList<T>>();
		int d = size() / v < 1 ? 1 : size() / v;

		for(int i = 0; i < v + 1; i++)
		{
			GList<T> l = new GList<T>();

			for(int j = 0; j < d; j++)
			{
				if(!isEmpty())
				{
					l.add(pop());
				}
			}

			mtt.add(l);
		}

		return mtt;
	}

	/**
	 * Cuts this list in half, returns a list of this list type, Basically List - 1
	 * - 2 - 3 - 4 Would return List - List - - 1 - - 2 - List - - 3 - - 4
	 *
	 * @return a split set of lists
	 */
	public GList<GList<T>> split()
	{
		return split(2);
	}

	/**
	 * Does this list contain the given index?
	 *
	 * @param i
	 *            the given index
	 * @return true if the list has the given index
	 */
	public boolean hasIndex(int i)
	{
		return i >= 0 && i < size();
	}

	/**
	 * Pick a random element in the list
	 *
	 * @return the randomly picked element
	 */
	public T pickRandom()
	{
		Random random = new Random();
		return get(random.nextInt(size()));
	}

	/**
	 * Get a GList of Strings from the elements in this list. Essentially creates a
	 * list of objects toString'd into a new list
	 *
	 * @return the String list
	 */
	public GList<String> stringList()
	{
		GList<String> s = new GList<String>();

		for(T i : this)
		{
			s.add(i.toString());
		}

		return s;
	}

	/**
	 * Do something for each
	 *
	 * @param callback
	 *            the something to do things for something
	 */
	public void forEach(Callback<T> callback)
	{
		for(T i : this)
		{
			callback.run(i);
		}
	}

	/**
	 * Get the last index of the list
	 *
	 * @return the last index
	 */
	public int last()
	{
		return size() - 1;
	}

	/**
	 * Get the index at the given index (same) OR if that index does not exist, get
	 * the LAST index of this list
	 *
	 * @param index
	 *            the index
	 * @return the same index, or the last index of the list if the given inxex does
	 *         not exist
	 */
	public int getIndexOrLast(int index)
	{
		if(hasIndex(index))
		{
			return index;
		}

		return last();
	}

	/**
	 * Crop out the end of the list by supplying a START index to be the next 0 of
	 * the new cropped list <br/>
	 * <br/>
	 * Example List a, b, c, d <br/>
	 * cropFrom(1) > c, d
	 *
	 * @param from
	 *            the from index to be the new beginning of the next index
	 * @return the cropped glist
	 */
	public GList<T> cropFrom(int from)
	{
		return crop(from, size() - 1);
	}

	/**
	 * Crop out the beginning of the list by supplying an END index to be the next
	 * end index of the new cropped list <br/>
	 * <br/>
	 * Example List a, b, c, d <br/>
	 * cropFrom(1) > a, b
	 *
	 * @param from
	 *            the from index to be the new beginning of the next index
	 * @return the cropped glist
	 */
	public GList<T> cropTo(int to)
	{
		return crop(0, to);
	}

	/**
	 * Crop the glist <br/>
	 * <br/>
	 * Example List a, b, c, d <br/>
	 * cropFrom(1, 2) > b, c
	 *
	 * @param from
	 *            the from index
	 * @param to
	 *            the to index
	 * @return the cropped glist
	 */
	public GList<T> crop(int from, int to)
	{
		GList<T> crop = new GList<T>();

		if(!isEmpty() && from >= 0 && hasIndex(from) && hasIndex(to) && from <= to)
		{
			for(int i = from; i <= to; i++)
			{
				crop.add(get(i));
			}
		}

		return crop;
	}

	/**
	 * Remove all duplicates from this list (by creating a set and adding them back
	 * to this list. NOT A COPY.
	 *
	 * @return the new list
	 */
	public GList<T> removeDuplicates()
	{
		Set<T> set = new LinkedHashSet<T>(this);
		clear();
		addAll(set);

		return this;
	}

	/**
	 * Remove all of the given object in the list.
	 *
	 * @param t
	 *            the given object
	 */
	public void removeAll(T t)
	{
		while(contains(t))
		{
			remove(t);
		}
	}

	public GSet<T> toSet()
	{
		return new GSet<T>(this);
	}

	/**
	 * Does this list have duplicates?
	 *
	 * @return true if there is at least one duplicate element
	 */
	public boolean hasDuplicates()
	{
		return size() != new LinkedHashSet<T>(this).size();
	}

	/**
	 * Sort the list by comparing them via toStrings
	 */
	public void sort()
	{
		sort(null);
	}

	public GList<T> sortCopy()
	{
		GList<T> m = copy();
		m.sort(null);
		return m;
	}

	/**
	 * Add a new element to the list, and remove the first element if the limit is
	 * reached (size)
	 *
	 * @param value
	 *            the new element
	 * @param limit
	 *            the limit of this list's size
	 */
	public void push(T value, int limit)
	{
		add(value);

		while(size() > limit && !isEmpty())
		{
			remove(0);
		}
	}

	/**
	 * Add an array of items of the same type, or all of them (...)
	 *
	 * @param array
	 *            the array
	 */
	@SuppressWarnings("unchecked")
	public void add(T... array)
	{
		for(T i : array)
		{
			add(i);
		}
	}

	/**
	 * Add an element to the list and return it, great for chaining
	 *
	 * @param t
	 *            the element to add to the end
	 * @return this list (for chaining)
	 */
	public GList<T> qadd(T t)
	{
		this.add(t);
		return this;
	}

	public GList<T> qadd(T[] t)
	{
		this.add(t);
		return this;
	}

	/**
	 * Add a list of elements to the list (same type)
	 *
	 * @param array
	 *            the list
	 */
	public void add(List<T> array)
	{
		for(T i : array)
		{
			add(i);
		}
	}

	/**
	 * Get a string of this list with a split string added between the elements. For
	 * example if you pass in ", " it would return a comma+space separated list.
	 *
	 * @param split
	 *            the split string
	 * @return a string
	 */
	public String toString(String split)
	{
		if(isEmpty())
		{
			return "";
		}

		if(size() == 1)
		{
			if(get(0) != null)
			{
				return get(0).toString();
			}

			return "";
		}

		String s = "";

		if(split == null)
		{
			split = "";
		}

		for(Object i : this)
		{
			s = s + split + i.toString();
		}

		if(s.length() == 0)
		{
			return "";
		}

		return s.substring(split.length());
	}

	/**
	 * Get a reversed copy of the list
	 *
	 * @return the reversed list (copied)
	 */
	public GList<T> reverse()
	{
		Collections.reverse(this);
		return this;
	}

	/**
	 * Comma, space, separated, list, representation
	 */
	@Override
	public String toString()
	{
		return toString(", ");
	}

	@Override
	public GList<T> clone()
	{
		return copy();
	}

	/**
	 * Copy is an implementation specific clone
	 *
	 * @return cloned list
	 */
	public GList<T> copy()
	{
		GList<T> c = new GList<T>();

		for(T i : this)
		{
			c.add(i);
		}

		return c;
	}

	/**
	 * Delete chain an item
	 *
	 * @param t
	 *            the element
	 * @return the new list
	 */
	public GList<T> qdel(T t)
	{
		remove(t);
		return this;
	}

	/**
	 * Return the first element in the list (0), then delete it
	 *
	 * @return the popped element
	 */
	public T pop()
	{
		if(isEmpty())
		{
			return null;
		}

		T t = get(0);
		remove(0);
		return t;
	}

	/**
	 * Pop and run the first element off the list
	 *
	 * @param c
	 *            the callback to run
	 */
	public void run(Callback<T> c)
	{
		c.run(pop());
	}

	/**
	 * Pop and run the last element off the list
	 *
	 * @param c
	 *            the callback to run
	 */
	public void runLast(Callback<T> c)
	{
		c.run(popLast());
	}

	/**
	 * Convert this list into a list of strings (toString)
	 *
	 * @return the string list
	 */
	public GList<String> toStringList()
	{
		GList<String> s = new GList<String>();

		for(T i : this)
		{
			s.add(i.toString());
		}

		return s;
	}

	/**
	 * Pop and run everything off the list. The list will be empty after everything
	 * has been run
	 *
	 * @param c
	 *            the callback to run
	 */
	public void runAll(Callback<T> c)
	{
		while(!isEmpty())
		{
			run(c);
		}
	}

	/**
	 * Remove the last element
	 */
	public GList<T> removeLast()
	{
		remove(last());

		return this;
	}

	/**
	 * Return the last element in the list, then delete it
	 *
	 * @return the popped element
	 */
	public T popLast()
	{
		if(isEmpty())
		{
			return null;
		}

		T t = get(last());
		remove(last());
		return t;
	}

	/**
	 * Pop a random element off the list
	 *
	 * @return the random element
	 */
	public T popRandom()
	{
		GList<T> tx = shuffleCopy();

		if(tx.isEmpty())
		{
			return null;
		}

		T t = tx.get(0);
		remove(t);
		return t;
	}

	public void fill(T t, int amt)
	{
		for(int i = 0; i < amt; i++)
		{
			add(t);
		}
	}

	public static GList<String> asStringList(List<?> oo)
	{
		GList<String> s = new GList<String>();

		for(Object i : oo)
		{
			s.add(i.toString());
		}

		return s;
	}

	public static GList<String> fromJSONAny(JSONArray oo)
	{
		GList<String> s = new GList<String>();

		for(int i = 0; i < oo.length(); i++)
		{
			s.add(oo.get(i).toString());
		}

		return s;
	}
}
