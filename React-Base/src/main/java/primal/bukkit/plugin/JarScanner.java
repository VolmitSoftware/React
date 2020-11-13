package primal.bukkit.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import primal.lang.collection.GSet;

/**
 * Scans jars for loaded classes into a collection
 *
 * @author cyberpwn
 */
public class JarScanner
{
	private final GSet<Class<?>> classes;
	private final File jar;

	/**
	 * Create a scanner
	 *
	 * @param jar
	 *            the path to the jar
	 */
	public JarScanner(File jar)
	{
		this.jar = jar;
		this.classes = new GSet<Class<?>>();
	}

	/**
	 * Scan the jar
	 *
	 * @throws IOException
	 *             bad things happen
	 */
	public void scan() throws IOException
	{
		classes.clear();
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

				try
				{
					Class<?> clazz = Class.forName(c);
					classes.add(clazz);
				}

				catch(Throwable e)
				{

				}
			}
		}

		zip.close();
	}

	/**
	 * Get the scanned clases
	 *
	 * @return a gset of classes
	 */
	public GSet<Class<?>> getClasses()
	{
		return classes;
	}

	/**
	 * Get the file object for the jar
	 *
	 * @return a file object representing the jar
	 */
	public File getJar()
	{
		return jar;
	}
}
