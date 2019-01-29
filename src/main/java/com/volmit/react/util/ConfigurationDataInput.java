package com.volmit.react.util;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

import com.volmit.volume.lang.collections.GMap;

public class ConfigurationDataInput
{
	@SuppressWarnings("unchecked")
	public void read(IConfigurable c, File f) throws Exception
	{
		DataCluster current = new DataCluster();
		Class<? extends IConfigurable> clazz = c.getClass();
		GMap<Integer, String> keystore = null;

		if(!f.exists())
		{
			current = fillDefaults(c);
			new YamlDataOutput().write(current, f);
		}

		current = new YamlDataInput().read(f);

		for(Field i : clazz.getDeclaredFields())
		{
			if(Modifier.isStatic(i.getModifiers()) || Modifier.isFinal(i.getModifiers()) || !Modifier.isPublic(i.getModifiers()) || !i.isAnnotationPresent(Key.class))
			{
				if(i.isAnnotationPresent(Key.class))
				{
					D.w("Field: " + i.getName() + " is either static, final, or private");
				}

				continue;
			}

			Key k = i.getAnnotation(Key.class);
			Object raw = current.get(k.value());

			switch(current.getType(k.value()))
			{
				case BOOLEAN:
					i.set(c, (Boolean) raw);
					break;
				case DOUBLE:
					i.set(c, (Double) raw);
					break;
				case INT:
					i.set(c, (Integer) raw);
					break;
				case LONG:
					i.set(c, (Long) raw);
					break;
				case STRING:
					i.set(c, (String) raw);
					break;
				case STRING_LIST:
					i.set(c, (ArrayList<String>) raw);
					break;
			}
		}

		for(Field i : clazz.getDeclaredFields())
		{
			if(!Modifier.isStatic(i.getModifiers()) || Modifier.isFinal(i.getModifiers()) || !Modifier.isPublic(i.getModifiers()) || !i.isAnnotationPresent(KeyStore.class))
			{
				continue;
			}

			Object o = i.get(null);

			if(o instanceof GMap)
			{
				keystore = (GMap<Integer, String>) o;
				break;
			}
		}

		if(keystore != null)
		{
			for(Field i : clazz.getDeclaredFields())
			{
				if(Modifier.isStatic(i.getModifiers()) || Modifier.isFinal(i.getModifiers()) || !Modifier.isPublic(i.getModifiers()) || !i.isAnnotationPresent(KeyPointer.class))
				{
					continue;
				}

				KeyPointer k = i.getAnnotation(KeyPointer.class);
				int point = k.value();
				String skey = keystore.get(point);

				if(skey == null)
				{
					D.f("Cannot find key for " + point + " (" + i.getDeclaringClass().getSimpleName() + ":" + i.getName() + ")");
					continue;
				}

				Object raw = current.get(skey);

				switch(current.getType(skey))
				{
					case BOOLEAN:
						i.set(c, (Boolean) raw);
						break;
					case DOUBLE:
						i.set(c, (Double) raw);
						break;
					case INT:
						i.set(c, (Integer) raw);
						break;
					case LONG:
						i.set(c, (Long) raw);
						break;
					case STRING:
						i.set(c, (String) raw);
						break;
					case STRING_LIST:
						i.set(c, (ArrayList<String>) raw);
						break;
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	public DataCluster fillDefaults(IConfigurable c) throws ReflectiveOperationException
	{
		DataCluster cc = new DataCluster();
		Class<? extends IConfigurable> clazz = c.getClass();
		GMap<Integer, String> keystore = null;

		for(Field i : clazz.getDeclaredFields())
		{
			if(Modifier.isStatic(i.getModifiers()) || Modifier.isFinal(i.getModifiers()) || !Modifier.isPublic(i.getModifiers()) || !i.isAnnotationPresent(Key.class))
			{
				continue;
			}

			Key k = i.getAnnotation(Key.class);
			String key = k.value();
			Object val = i.get(c);

			if(val instanceof Integer)
			{
				cc.set(key, (Integer) val);
			}

			else if(val instanceof String)
			{
				cc.set(key, (String) val);
			}

			else if(val instanceof Double)
			{
				cc.set(key, (Double) val);
			}

			else if(val instanceof Boolean)
			{
				cc.set(key, (Boolean) val);
			}

			else if(val instanceof Long)
			{
				cc.set(key, (Long) val);
			}

			else if(val instanceof ArrayList)
			{
				cc.set(key, (ArrayList<String>) val);
			}

			else
			{
				throw new ReflectiveOperationException("Unknown TYPE: " + val.getClass());
			}
		}

		for(Field i : clazz.getDeclaredFields())
		{
			if(!Modifier.isStatic(i.getModifiers()) || Modifier.isFinal(i.getModifiers()) || !Modifier.isPublic(i.getModifiers()) || !i.isAnnotationPresent(KeyStore.class))
			{
				continue;
			}

			Object o = i.get(null);

			if(o instanceof GMap)
			{
				keystore = (GMap<Integer, String>) o;
				break;
			}
		}

		if(keystore != null)
		{
			for(Field i : clazz.getDeclaredFields())
			{
				if(Modifier.isStatic(i.getModifiers()) || Modifier.isFinal(i.getModifiers()) || !Modifier.isPublic(i.getModifiers()) || !i.isAnnotationPresent(KeyPointer.class))
				{
					continue;
				}

				KeyPointer k = i.getAnnotation(KeyPointer.class);
				int point = k.value();
				String skey = keystore.get(point);

				if(skey == null)
				{
					D.f("Cannot find key for " + point + " (" + i.getDeclaringClass().getSimpleName() + ":" + i.getName() + ")");
					continue;
				}

				Object val = i.get(c);

				if(val instanceof Integer)
				{
					cc.set(skey, (Integer) val);
				}

				else if(val instanceof String)
				{
					cc.set(skey, (String) val);
				}

				else if(val instanceof Double)
				{
					cc.set(skey, (Double) val);
				}

				else if(val instanceof Boolean)
				{
					cc.set(skey, (Boolean) val);
				}

				else if(val instanceof Long)
				{
					cc.set(skey, (Long) val);
				}

				else if(val instanceof ArrayList)
				{
					cc.set(skey, (ArrayList<String>) val);
				}

				else
				{
					throw new ReflectiveOperationException("Unknown TYPE: " + val.getClass());
				}
			}
		}

		return cc;
	}
}
