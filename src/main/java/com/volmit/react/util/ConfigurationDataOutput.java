package com.volmit.react.util;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import com.volmit.volume.lang.collections.GMap;

public class ConfigurationDataOutput
{
	@SuppressWarnings("unchecked")
	public void write(IConfigurable c, File file) throws Exception
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

			else if(val instanceof List)
			{
				cc.set(key, (List<String>) val);
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

				else if(val instanceof List)
				{
					cc.set(skey, (List<String>) val);
				}

				else
				{
					throw new ReflectiveOperationException("Unknown TYPE: " + val.getClass());
				}
			}
		}

		new YamlDataOutput().write(cc, file);
	}
}
