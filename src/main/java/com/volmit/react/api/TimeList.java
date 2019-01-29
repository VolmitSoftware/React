package com.volmit.react.api;

import java.lang.reflect.Field;
import java.util.Queue;

import org.spigotmc.CustomTimingsHandler;

import com.volmit.volume.lang.collections.GSet;
import com.volmit.volume.reflect.V;

public class TimeList
{
	private GSet<TimedElement> e;

	public TimeList()
	{
		e = new GSet<TimedElement>();
	}

	public void poll() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException
	{
		e.clear();
		if(Flavor.getHostFlavor().equals(Flavor.SPIGOT))
		{
			pollSpigot();
		}

		if(Flavor.getHostFlavor().equals(Flavor.PAPER_SPIGOT))
		{
			pollPaper();
		}
	}

	@SuppressWarnings("unchecked")
	private void pollSpigot() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException
	{
		Field f = CustomTimingsHandler.class.getDeclaredField("HANDLERS");
		f.setAccessible(true);
		Queue<CustomTimingsHandler> q = (Queue<CustomTimingsHandler>) f.get(null);
		GSet<TimedElement> el = new GSet<TimedElement>();

		for(CustomTimingsHandler i : q)
		{
			TimedElement t = new TimedElement(new V(i).get("name"), new V(i).get("count"), new V(i).get("start"), new V(i).get("timingDepth"), new V(i).get("totalTime"), new V(i).get("curTickTotal"), new V(i).get("violations"));
			el.add(t);
		}

		for(CustomTimingsHandler i : q)
		{
			TimedElement t = null;
			CustomTimingsHandler parent = new V(i).get("parent");

			for(TimedElement j : el)
			{
				if(new V(i).get("name").equals(j.getName()))
				{
					t = j;
					break;
				}
			}

			if(t == null)
			{
				continue;
			}

			if(parent != null)
			{
				for(TimedElement j : el)
				{
					if(j.getName().equals(new V(parent).get("name")))
					{
						t.setParent(j);
						break;
					}
				}
			}
		}

		e = el;
	}

	private void pollPaper()
	{
		// TODO Auto-generated method stub

	}

	public class TimedElement
	{
		private String name;
		private TimedElement parent;
		private long count;
		private long start;
		private long timingDepth;
		private long totalTime;
		private long curTickTotal;
		private long violations;

		public TimedElement(String name, long count, long start, long timingDepth, long totalTime, long curTickTotal, long violations)
		{
			this.name = name;
			this.count = count;
			this.start = start;
			this.timingDepth = timingDepth;
			this.totalTime = totalTime;
			this.curTickTotal = curTickTotal;
			this.violations = violations;
			parent = null;
		}

		@Override
		public String toString()
		{
			return name + " -> count: " + count + " start: " + start + " dep: " + timingDepth + " total: " + totalTime + " cur: " + curTickTotal + " v: " + violations;
		}

		public void setParent(TimedElement parent)
		{
			this.parent = parent;
		}

		public boolean hasParent()
		{
			return getParent() != null;
		}

		public TimedElement getParent()
		{
			return parent;
		}

		public String getName()
		{
			return name;
		}

		public long getCount()
		{
			return count;
		}

		public long getStart()
		{
			return start;
		}

		public long getTimingDepth()
		{
			return timingDepth;
		}

		public long getTotalTime()
		{
			return totalTime;
		}

		public long getCurTickTotal()
		{
			return curTickTotal;
		}

		public long getViolations()
		{
			return violations;
		}

		@Override
		public int hashCode()
		{
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + (int) (count ^ (count >>> 32));
			result = prime * result + (int) (curTickTotal ^ (curTickTotal >>> 32));
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			result = prime * result + ((parent == null) ? 0 : parent.hashCode());
			result = prime * result + (int) (start ^ (start >>> 32));
			result = prime * result + (int) (timingDepth ^ (timingDepth >>> 32));
			result = prime * result + (int) (totalTime ^ (totalTime >>> 32));
			result = prime * result + (int) (violations ^ (violations >>> 32));
			return result;
		}

		@Override
		public boolean equals(Object obj)
		{
			if(this == obj)
			{
				return true;
			}
			if(obj == null)
			{
				return false;
			}
			if(getClass() != obj.getClass())
			{
				return false;
			}
			TimedElement other = (TimedElement) obj;
			if(!getOuterType().equals(other.getOuterType()))
			{
				return false;
			}
			if(count != other.count)
			{
				return false;
			}
			if(curTickTotal != other.curTickTotal)
			{
				return false;
			}
			if(name == null)
			{
				if(other.name != null)
				{
					return false;
				}
			}
			else if(!name.equals(other.name))
			{
				return false;
			}
			if(parent == null)
			{
				if(other.parent != null)
				{
					return false;
				}
			}
			else if(!parent.equals(other.parent))
			{
				return false;
			}
			if(start != other.start)
			{
				return false;
			}
			if(timingDepth != other.timingDepth)
			{
				return false;
			}
			if(totalTime != other.totalTime)
			{
				return false;
			}
			if(violations != other.violations)
			{
				return false;
			}
			return true;
		}

		private TimeList getOuterType()
		{
			return TimeList.this;
		}
	}
}
