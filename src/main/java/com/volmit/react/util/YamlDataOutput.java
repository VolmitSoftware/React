package com.volmit.react.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import com.volmit.volume.lang.collections.GList;

public class YamlDataOutput implements IDataOutput
{
	@Override
	public void write(DataCluster c, File f)
	{
		try
		{
			c.toFileConfiguration().save(f);
			GList<String> lines = new GList<String>();
			GList<String> newLines = new GList<String>();
			BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
			String line;

			while((line = r.readLine()) != null)
			{
				lines.add(line);
			}

			r.close();

			FileOutputStream fos = new FileOutputStream(f);
			PrintWriter pw = new PrintWriter(fos);

			for(String i : lines)
			{
				if(!i.contains(": "))
				{
					newLines.add(i);
					continue;
				}

				for(String j : c.keys())
				{
					if(j.contains("."))
					{
						GList<String> cs = new GList<String>(j.split("\\."));
						int spaces = i.length() - i.trim().length();
						int indents = spaces / 2;

						if(cs.get(cs.last()).equals(i.trim().split(": ")[0]))
						{
							if(indents == cs.size() - 1)
							{
								if(c.hasComment(j))
								{
									newLines.add("");
									String s = F.wrapWords(c.getComment(j), 77);

									if(s.contains("\n"))
									{
										for(String k : s.split("\n"))
										{
											newLines.add(F.repeat(" ", spaces) + "# " + k);
										}
									}

									else
									{
										newLines.add(F.repeat(" ", spaces) + "# " + s);
									}
								}
							}

							break;
						}
					}

					else
					{
						// TODO handle no dot
					}
				}

				newLines.add(i);
			}

			for(String i : newLines)
			{
				pw.println(i);
			}

			pw.close();
		}

		catch(Throwable e)
		{
			Ex.t(e);
		}
	}
}
