package primal.bukkit.config;

import java.io.File;

import primal.lang.collection.GList;

public interface ConfigWrapper
{
	void load(File f) throws Exception;

	void save(File f) throws Exception;

	String save();

	void load(String string) throws Exception;

	void set(String key, Object o);

	Object get(String key);

	GList<String> keys();

	boolean contains(String key);
}
