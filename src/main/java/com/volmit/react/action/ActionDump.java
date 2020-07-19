package com.volmit.react.action;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;

import com.volmit.react.util.*;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.volmit.react.Gate;
import com.volmit.react.ReactPlugin;
import com.volmit.react.api.Action;
import com.volmit.react.api.ActionType;
import com.volmit.react.api.IActionSource;
import com.volmit.react.api.ISelector;
import com.volmit.react.api.PlayerActionSource;
import com.volmit.react.api.RAI;

import org.json.JSONObject;
import primal.util.text.C;

public class ActionDump extends Action
{
	public ActionDump()
	{
		super(ActionType.DUMP);
		setNodes(new String[] {"dump", "du", "dmp", "support", "vsu"});
	}

	@Override
	public void enact(IActionSource as, ISelector... selectors)
	{
		as.sendResponseActing("Collecting Information, Please Wait.");

		if(isForceful())
		{
			try
			{
				System.out.println("[React Dump] Forcing a debug!");
				String urlDump = Paste.paste(Gate.dump().toString(4)) + ".json";
				as.sendResponseSuccess("Cha-Ching " + C.WHITE + urlDump);
				completeAction();
				return;
			}

			catch(Exception e)
			{
				e.printStackTrace();
			}
		}

		new A()
		{
			@Override
			public void run()
			{
				try
				{
					System.out.println("[React Dump] Dumping configurations");
					JSONObject js = new JSONObject();
					String urlDump = Paste.paste(Gate.dump().toString(4));
					String urlConfig = readFile(new File(ReactPlugin.i.getDataFolder(), "config.yml"));
					String urlGoals = RAI.instance.getGoalManager().saveGoalsToPaste().split(" ")[1].trim() + ".json";
					js.put("dump", urlDump + ".json");
					js.put("config", urlConfig);
					js.put("goals", urlGoals);
					long time = 215000;
					int inter = 196;
					new S("dw")
					{
						@Override
						public void run()
						{
							System.out.println("[React Dump] Collecting timings report");
							long ms = M.ms();

							new Task("task", inter)
							{
								@Override
								public void run()
								{
									long elapsed = M.ms() - ms;

									if(elapsed < 2500)
									{
										return;
									}

									if(elapsed > time - 1234)
									{
										cancel();
									}

									else
									{
										as.sendResponseActing("Collecting: " + C.WHITE + F.pc((double) elapsed / (double) time, 0) + " (about " + F.timeLong(time - elapsed, 0) + " left)");
									}
								}
							};

							Gate.pullTimingsReport(time, new Callback<String>()
							{
								@Override
								public void run(String t)
								{
									new A()
									{
										@Override
										public void run()
										{
											String vt = t;

											if(vt == null || vt.length() == 0)
											{
												vt = "CHECK PAPER CONSOLE FOR TIMINGS LINK (unsupported)";
											}

											js.put("timings", vt);
											String url;

											try
											{
												url = Paste.paste(js.toString(4)) + ".json";

												new S("tfin")
												{
													@Override
													public void run()
													{
														Gate.msgSuccess(((PlayerActionSource) as).getPlayer(), "Dump Package Created: " + C.WHITE + url);
														completeAction();
													}
												};
											}

											catch(Exception e)
											{
												e.printStackTrace();
											}
										}
									};
								}
							});
						}
					};
				}

				catch(Throwable e)
				{
					e.printStackTrace();
				}
			}
		};
	}

	private String readFile(File file) throws IOException, ParseException, org.json.simple.parser.ParseException
	{
		String c = "";
		String l = "";
		BufferedReader bu = new BufferedReader(new FileReader(file));

		while((l = bu.readLine()) != null)
		{
			c += l + "\n";
		}

		bu.close();

		return Paste.paste(c) + "." + file.getName().split("\\.")[file.getName().split("\\.").length - 1];
	}

	@Override
	public String getNode()
	{
		return "dump";
	}

	@Override
	public ItemStack getIcon()
	{
		return new ItemStack(Material.BOOKSHELF);
	}
}
