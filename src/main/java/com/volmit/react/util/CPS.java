package com.volmit.react.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.GMap;

public class CPS
{
	private static GMap<String, GList<String>> keys = new GMap<String, GList<String>>();

	public static String format(StackTraceElement e)
	{
		return C.GRAY + e.getClassName() + "." + C.WHITE + e.getMethodName() + C.GRAY + "(" + C.RED + e.getLineNumber() + C.GRAY + ")";
	}

	public static GList<Plugin> identify(String clazz)
	{
		GList<Plugin> plgs = new GList<Plugin>();

		searching: for(String i : keys.k())
		{
			for(String j : keys.get(i))
			{
				if(j.equals(clazz))
				{
					plgs.add(Bukkit.getPluginManager().getPlugin(i));
					continue searching;
				}
			}
		}

		return plgs;
	}

	public static void scan() throws IOException
	{
		File pluginsFolder = new File("plugins");

		for(File i : pluginsFolder.listFiles())
		{
			if(!i.isFile() || !i.getName().endsWith(".jar"))
			{
				continue;
			}

			GList<String> names = new GList<String>();
			String plugin = null;
			File jar = i;
			FileInputStream fin = new FileInputStream(jar);
			ZipInputStream zip = new ZipInputStream(fin);

			for(ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
			{
				if(!entry.isDirectory() && entry.getName().endsWith(".class"))
				{
					if(entry.getName().contains("$"))
					{
						continue;
					}

					String c = entry.getName().replaceAll("/", ".").replace(".class", "");
					names.add(c);
				}

				if(!entry.isDirectory() && !entry.getName().endsWith(".class"))
				{
					if(entry.getName().endsWith("plugin.yml"))
					{
						String content = readResource(jar, entry.getName());

						for(String j : content.split("\n"))
						{
							if(j.startsWith("name:"))
							{
								plugin = j.split(":")[1].trim();
							}
						}
					}
				}
			}

			zip.close();

			if(plugin != null)
			{
				keys.put(plugin, names);
				D.l("Identified " + plugin + " with " + names.size() + " signatures.");
			}
		}
	}

	private static String readResource(File f, String resource) throws IOException
	{
		String target = resource;
		File jar = f;
		ZipFile zipFile = new ZipFile(jar);
		Enumeration<? extends ZipEntry> entries = zipFile.entries();

		while(entries.hasMoreElements())
		{
			ZipEntry entry = entries.nextElement();

			if(entry.getName().equals(target))
			{
				InputStream stream = zipFile.getInputStream(entry);
				InputStreamReader reads = new InputStreamReader(stream);
				BufferedReader reader = new BufferedReader(reads);
				String src = "";
				String line;

				while((line = reader.readLine()) != null)
				{
					src = src + line + "\n";
				}

				reader.close();

				return src;
			}
		}

		zipFile.close();

		return null;
	}
}
