package com.volmit.react;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import com.volmit.react.api.Capability;
import com.volmit.react.api.SampledType;
import com.volmit.react.util.F;

public class PlaceholderHandler extends PlaceholderExpansion
{

	/**
	 * Because this is an internal class,
	 * you must override this method to let PlaceholderAPI know to not unregister your expansion class when
	 * PlaceholderAPI is reloaded
	 *
	 * @return true to persist through reloads
	 */
	@Override
	public boolean persist()
	{
		return true;
	}

	/**
	 * Because this is a internal class, this check is not needed
	 * and we can simply return {@code true}
	 *
	 * @return Always true since it's an internal class.
	 */
	@Override
	public boolean canRegister()
	{
		return true;
	}

	/**
	 * The name of the person who created this expansion should go here.
	 * <br>For convienience do we return the author from the plugin.yml
	 *
	 * @return The name of the author as a String.
	 */
	@Override
	public String getAuthor()
	{
		return ReactPlugin.i.getDescription().getAuthors().toString();
	}

	/**
	 * The placeholder identifier should go here.
	 * <br>This is what tells PlaceholderAPI to call our onRequest
	 * method to obtain a value if a placeholder starts with our
	 * identifier.
	 * <br>This must be unique and can not contain % or _
	 *
	 * @return The identifier in {@code %<identifier>_<value>%} as String.
	 */
	@Override
	public String getIdentifier()
	{
		return "react";
	}

	/**
	 * This is the version of the expansion.
	 * <br>You don't have to use numbers, since it is set as a String.
	 *
	 * For convienience do we return the version from the plugin.yml
	 *
	 * @return The version as a String.
	 */
	@Override
	public String getVersion()
	{
		return ReactPlugin.i.getDescription().getVersion();
	}

	public void writeToFile() throws IOException
	{
		File f = new File(ReactPlugin.i.getDataFolder(), "placeholders.txt");
		f.getParentFile().mkdirs();
		PrintWriter pw = new PrintWriter(f);

		pw.println("# You can use the following suffixes for samplers");
		pw.println("_raw -> Raw digits up to 9");
		pw.println("_raw_0 -> Raw, no decimals");
		pw.println("_raw_1 -> Raw, 1 decimal");
		pw.println("_raw_2 -> Raw, 2 decimals");
		pw.println("_raw_3 -> Raw, 3 decimals");
		pw.println("_raw_4 -> Raw, 4 decimals");
		pw.println("_raw_force_1 -> Raw, forces 1 decimal. If the value is round, places 0.");
		pw.println("_raw_force_2 -> Raw, forces 2 decimals. If no decimal is set for any segment, zero is used. I.e. 19.00 (instead of 19)");

		pw.println();
		pw.println("%react_ping%");
		pw.println("%react_ping_raw%");
		pw.println("%react_ping_flat%");

		pw.println();
		pw.println("# Samplers support suffixes");
		for(SampledType i : SampledType.values())
		{
			pw.println("%react_sample_" + i.name().toLowerCase() + "%");
			pw.println("%react_sample_" + i.name().toLowerCase() + "_raw%");
		}

		pw.close();
	}

	@Override
	public String onPlaceholderRequest(Player p, String s)
	{
		if(s.startsWith("sample_"))
		{
			for(SampledType i : SampledType.values())
			{
				if(s.startsWith("sample_" + i.name().toLowerCase()))
				{
					if(s.endsWith("_raw"))
					{
						return "" + F.f(i.get().getValue(), 9);
					}

					else if(s.endsWith("_raw_force_1"))
					{
						return "" + F.fd(i.get().getValue(), 1);
					}

					else if(s.endsWith("_raw_force_2"))
					{
						return "" + F.fd(i.get().getValue(), 2);
					}

					else if(s.endsWith("_raw_1"))
					{
						return "" + F.f(i.get().getValue(), 1);
					}

					else if(s.endsWith("_raw_2"))
					{
						return "" + F.f(i.get().getValue(), 2);
					}

					else if(s.endsWith("_raw_3"))
					{
						return "" + F.f(i.get().getValue(), 3);
					}

					else if(s.endsWith("_raw_4"))
					{
						return "" + F.f(i.get().getValue(), 4);
					}

					else if(s.endsWith("_raw_0"))
					{
						return "" + F.f(i.get().getValue(), 0);
					}

					return i.get().get();
				}
			}
		}

		if(s.startsWith("ping"))
		{
			if(!Capability.ACCELERATED_PING.isCapable())
			{
				return "NCA";
			}

			if(s.equals("ping") || s.equals("ping_raw") || s.equals("ping_flat"))
			{
				if(p == null)
				{
					return "NPI";
				}

				if(s.endsWith("_raw"))
				{
					return "" + React.instance.protocolController.ping(p);
				}

				else if(s.endsWith("_flat"))
				{
					return F.time(React.instance.protocolController.ping(p), 0);
				}

				else
				{
					return F.time(React.instance.protocolController.ping(p), 2);
				}
			}
		}

		return null;
	}
}
