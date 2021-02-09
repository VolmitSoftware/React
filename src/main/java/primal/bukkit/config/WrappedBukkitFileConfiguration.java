package primal.bukkit.config;

import java.io.File;
import java.util.List;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import primal.lang.collection.GList;

public class WrappedBukkitFileConfiguration implements ConfigWrapper
{
	private FileConfiguration wrapped;

	public WrappedBukkitFileConfiguration()
	{
		wrapped = new YamlConfiguration();
	}

	@Override
	public void load(File f) throws Exception
	{
		wrapped.load(f);
	}

	@Override
	public void save(File f) throws Exception
	{
		wrapped.save(f);
	}

	@Override
	public String save()
	{
		return wrapped.saveToString();
	}

	@Override
	public void load(String s) throws Exception
	{
		wrapped.load(s);
	}

	@Override
	public void set(String key, Object oo)
	{
		Object o = null;

		if(oo instanceof List)
		{
			o = GList.asStringList((List<?>) oo);
		}

		else
		{
			o = oo;
		}

		wrapped.set(key, o);
	}

	@Override
	public Object get(String key)
	{
		Object o = wrapped.get(key);

		if(o instanceof List)
		{
			return GList.asStringList((List<?>) o);
		}

		return o;
	}

	@Override
	public GList<String> keys()
	{
		return new GList<String>(wrapped.getKeys(true));
	}

	@Override
	public boolean contains(String key)
	{
		return wrapped.contains(key);
	}
}
