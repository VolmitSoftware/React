package com.volmit.react.util;

import java.io.File;

import com.volmit.volume.lang.collections.GMap;

public class HotloadManager
{
	private GMap<File, Long> filemods;
	private GMap<File, Long> filesizes;
	private GMap<File, Runnable> fileacts;

	public HotloadManager()
	{
		filemods = new GMap<File, Long>();
		filesizes = new GMap<File, Long>();
		fileacts = new GMap<File, Runnable>();
	}

	public void track(File file, Runnable r)
	{
		filemods.put(file, file.lastModified());
		filesizes.put(file, file.length());
		fileacts.put(file, r);
		D.v("Now tracking for hotloads: " + file.getAbsolutePath());
	}

	public void untrack(File f)
	{
		filemods.remove(f);
		filesizes.remove(f);
		fileacts.remove(f);
	}

	public void untrackall()
	{
		filemods.clear();
		filesizes.clear();
		fileacts.clear();
	}

	public void onTick()
	{
		new A()
		{
			@Override
			public void run()
			{
				for(File i : filemods.k())
				{
					try
					{
						if(i.exists() && (i.lastModified() != filemods.get(i) || i.length() != filesizes.get(i)))
						{
							D.v(i.getName() + " modified");
							fileacts.get(i).run();
							filemods.put(i, i.lastModified());
							filesizes.put(i, i.length());
						}

						if(!i.exists() || i.isDirectory())
						{
							D.v(i.getName() + " deleted, untracking.");
							untrack(i);
						}
					}

					catch(Throwable e)
					{
						Ex.t(e);
					}
				}
			}
		};
	}

	public String getTickName()
	{
		return "hotloadmgr";
	}
}
