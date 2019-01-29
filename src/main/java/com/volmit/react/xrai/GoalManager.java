package com.volmit.react.xrai;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.text.ParseException;

import com.volmit.react.ReactPlugin;
import com.volmit.react.api.ActionType;
import com.volmit.react.api.ChunkIssue;
import com.volmit.react.api.Note;
import com.volmit.react.api.SampledType;
import com.volmit.react.util.JSONArray;
import com.volmit.react.util.JSONException;
import com.volmit.react.util.JSONObject;
import com.volmit.react.util.M;
import com.volmit.react.util.Paste;
import com.volmit.react.util.S;
import com.volmit.react.util.TaskLater;
import com.volmit.volume.lang.collections.GList;

public class GoalManager
{
	private GList<RAIGoal> goals;
	private File f;

	public GoalManager()
	{
		goals = new GList<RAIGoal>();
		f = new File(ReactPlugin.i.getDataFolder(), "goals");
		f.mkdirs();
	}

	public GList<RAIGoal> getGoals()
	{
		return goals;
	}

	public File getF()
	{
		return f;
	}

	public void tick()
	{
		for(RAIGoal i : goals)
		{
			if(!i.isEnabled())
			{
				continue;
			}

			i.regenHealth();

			if(i.getInterval() != -1)
			{
				if(M.ms() - i.getLast() < i.getInterval())
				{
					continue;
				}
			}

			if(i.getConditions().isMet() && i.damageHealth())
			{
				Note.RAI.bake("Reacting to goal " + i.getName());
				i.setLast(M.ms());
				i.getAction().execute();
			}
		}
	}

	public void forceResetDefaults()
	{
		File f = new File(ReactPlugin.i.getDataFolder(), "goals");

		for(File i : new File(f, "default").listFiles())
		{
			i.delete();
		}

		new File(f, "default").delete();
		resetDefaults();
	}

	public void createDefaultGoals()
	{
		resetDefaults();

		File ff = new File(f, "goals-help.txt");

		new TaskLater("", 25)
		{
			@Override
			public void run()
			{
				try
				{
					PrintWriter pw = new PrintWriter(new FileWriter(ff));
					pw.println("=== Sampler Codes ===");

					for(SampledType i : SampledType.values())
					{
						pw.println("'" + i.name().toLowerCase() + "':");
						pw.println("    " + i.get().getName());
						pw.println("    " + i.get().getDescription());
						pw.println("    i.e. " + i.get().getValue() + " -> " + i.get().get());
					}

					pw.println();
					pw.println("=== Action Codes ===");

					for(ActionType i : ActionType.values())
					{
						pw.println("'" + i.name().toLowerCase() + "':");
						pw.println("    " + i.getName());
						pw.println("    " + i.getDescription());
					}

					pw.println();
					pw.println("=== 'Near' Tags ===");

					for(ChunkIssue i : ChunkIssue.values())
					{
						pw.println("'" + i.name().toLowerCase() + "'");
					}

					pw.close();
				}

				catch(IOException e)
				{
					e.printStackTrace();
				}
			}
		};
	}

	public void writeGoal(RAIGoal g, File f)
	{
		try
		{
			PrintWriter pw = new PrintWriter(new FileWriter(f));
			pw.println(g.toJSON().toString(4));
			pw.close();
		}

		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	public void resetDefaults()
	{
		File v = new File(f, "default");
		boolean mf = false;

		if(!v.exists())
		{
			mf = true;
		}

		v.mkdirs();

		writeGoal(createPurgeChunks(), new File(v, "purge-chunks.json"));
		writeGoal(createCullEntities(), new File(v, "cull-entities.json"));
		writeGoal(createSuppressFluids(), new File(v, "suppress-fluids.json"));
		writeGoal(createSuppressRedstone(), new File(v, "suppress-redstone.json"));

		if(mf)
		{
			writeGoal(createPurgeChunks(), new File(f, "purge-chunks.json"));
			writeGoal(createCullEntities(), new File(f, "cull-entities.json"));
			writeGoal(createSuppressFluids(), new File(f, "suppress-fluids.json"));
			writeGoal(createSuppressRedstone(), new File(f, "suppress-redstone.json"));
		}

		if(!new File(v, "new").exists())
		{
			new File(v, "new").mkdirs();

			new S("")
			{
				@Override
				public void run()
				{
					forceResetDefaults();
				}
			};
		}
	}

	public RAIGoal createCullEntities()
	{
		RAIGoal purgeChunks = new RAIGoal();
		purgeChunks.setInterval(500);
		purgeChunks.setSv("10t");
		purgeChunks.setAuthor("React");
		purgeChunks.setName("Cull Entities");
		purgeChunks.setDescription("Culls Entities in entity dense areas");
		purgeChunks.setHealthRegen(1);
		purgeChunks.setMaxHealth(295);
		purgeChunks.setHealthDamage(126);

		ConditionSet cs = new ConditionSet();
		cs.getConditions().add(new Condition(SampledType.ENTITY_TIME, ConditionOp.GREATER, 7.5));
		purgeChunks.setConditions(cs);

		VirtualAction va = new VirtualAction(ActionType.CULL_ENTITIES);
		va.getOptions().put("near", ChunkIssue.ENTITY.name().toLowerCase());
		purgeChunks.setAction(va);

		return purgeChunks;
	}

	public RAIGoal createSuppressFluids()
	{
		RAIGoal purgeChunks = new RAIGoal();
		purgeChunks.setInterval(250);
		purgeChunks.setSv("5t");
		purgeChunks.setAuthor("React");
		purgeChunks.setName("Suppress Fluids");
		purgeChunks.setDescription("Reduces fluid flows");
		purgeChunks.setHealthRegen(1);
		purgeChunks.setMaxHealth(400);
		purgeChunks.setHealthDamage(70);
		purgeChunks.setEnabled(false);

		ConditionSet cs = new ConditionSet();
		cs.getConditions().add(new Condition(SampledType.FLUID_TICK_USAGE, ConditionOp.GREATER, 0.25));
		purgeChunks.setConditions(cs);

		VirtualAction va = new VirtualAction(ActionType.LOCK_FLUID);
		va.getOptions().put("near", ChunkIssue.FLUID.name().toLowerCase());
		va.getOptions().put("time", "15t");
		purgeChunks.setAction(va);

		return purgeChunks;
	}

	public RAIGoal createSuppressRedstone()
	{
		RAIGoal purgeChunks = new RAIGoal();
		purgeChunks.setInterval(1000);
		purgeChunks.setSv("1s");
		purgeChunks.setAuthor("React");
		purgeChunks.setEnabled(false);
		purgeChunks.setName("Suppress Redstone");
		purgeChunks.setDescription("Reduces Redstone usage");
		purgeChunks.setHealthRegen(1);
		purgeChunks.setMaxHealth(400);
		purgeChunks.setHealthDamage(70);
		ConditionSet cs = new ConditionSet();
		cs.getConditions().add(new Condition(SampledType.REDSTONE_TICK_USAGE, ConditionOp.GREATER, 0.33));
		purgeChunks.setConditions(cs);

		VirtualAction va = new VirtualAction(ActionType.LOCK_REDSTONE);
		va.getOptions().put("near", ChunkIssue.REDSTONE.name().toLowerCase());
		va.getOptions().put("time", "1s");
		purgeChunks.setAction(va);

		return purgeChunks;
	}

	public RAIGoal createPurgeChunks()
	{
		RAIGoal purgeChunks = new RAIGoal();
		purgeChunks.setInterval(60000);
		purgeChunks.setSv("1m");
		purgeChunks.setAuthor("React");
		purgeChunks.setName("Purge Chunks");
		purgeChunks.setDescription("Purges excess chunks every minute.");
		purgeChunks.setHealthRegen(3);
		purgeChunks.setMaxHealth(6700);
		purgeChunks.setHealthDamage(5250);
		ConditionSet cs = new ConditionSet();
		cs.getConditions().add(new Condition(SampledType.CHK, ConditionOp.GREATER, 1000));
		purgeChunks.setConditions(cs);

		VirtualAction va = new VirtualAction(ActionType.PURGE_CHUNKS);
		purgeChunks.setAction(va);

		return purgeChunks;
	}

	public void loadGoals()
	{
		for(File i : f.listFiles())
		{
			if(i.getName().endsWith("json") && i.isFile())
			{
				try
				{
					String l = "";
					String line = null;
					BufferedReader bu = new BufferedReader(new FileReader(i));

					while((line = bu.readLine()) != null)
					{
						l += line;
					}

					bu.close();
					JSONObject j = new JSONObject(l);
					RAIGoal g = new RAIGoal(j);
					goals.add(g);
					writeGoal(g, i);
				}

				catch(FileNotFoundException e1)
				{
					e1.printStackTrace();
				}

				catch(IOException e)
				{
					e.printStackTrace();
				}
			}
		}
	}

	public void saveGoals()
	{
		for(RAIGoal i : goals)
		{
			try
			{
				PrintWriter pw = new PrintWriter(new File(i.getName()));
				pw.println(i.toJSON().toString(4));
				pw.close();
			}

			catch(FileNotFoundException e)
			{
				e.printStackTrace();
			}
		}
	}

	public int loadGoalsFromPaste(String url)
	{
		String code = "";
		int v = 0;

		if(url.startsWith("http://"))
		{
			code = url.split("/")[url.split("/").length - 1];
		}

		else
		{
			code = url;
		}

		try
		{
			URL u = new URL("http://paste.volmit.com/raw/" + code);
			BufferedReader bu = new BufferedReader(new InputStreamReader(u.openStream()));
			String c = "";
			String l = "";

			while((l = bu.readLine()) != null)
			{
				c += l + "\n";
			}

			JSONArray ja = new JSONArray(c);

			for(int i = 0; i < ja.length(); i++)
			{
				v++;
				JSONObject j = ja.getJSONObject(i);
				RAIGoal g = new RAIGoal(j);
				goals.add(g);
			}
		}

		catch(Exception e)
		{
			e.printStackTrace();
		}

		saveGoals();

		return v;
	}

	public String saveGoalsToPaste()
	{
		JSONArray ja = new JSONArray();

		for(RAIGoal i : goals)
		{
			ja.put(i.toJSON());
		}

		try
		{
			String url = Paste.paste(ja.toString(4));
			return Paste.lastKey + " " + url;
		}

		catch(JSONException | IOException | ParseException | org.json.simple.parser.ParseException e)
		{
			e.printStackTrace();
		}

		return "?";
	}

	public void reloadGoals()
	{
		goals.clear();
		loadGoals();
	}
}
