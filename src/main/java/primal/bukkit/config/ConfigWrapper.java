package primal.bukkit.config;

import java.io.File;

import primal.lang.collection.GList;

public interface ConfigWrapper
{
	public void load(File f) throws Exception;

	public void save(File f) throws Exception;

	public String save();

	public void load(String string) throws Exception;

	public void set(String key, Object o);

	public Object get(String key);

	public GList<String> keys();

	public boolean contains(String key);
}
