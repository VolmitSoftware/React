package primal.bukkit.nms;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.craftbukkit.v1_11_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_11_R1.util.CraftMagicNumbers;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.server.v1_11_R1.Item;
import net.minecraft.server.v1_11_R1.MinecraftKey;
import primal.bukkit.plugin.PrimalPlugin;


public class AdvancementHolder11
{
	private NamespacedKey id;
	private String title, parent, trigger, icon, description, background, frame;
	private Integer subID = 0, amount = 0;
	private boolean announce, toast = true;
	private List<ItemStack> items;
	public List<AdvancementHolder11> holders = new ArrayList<AdvancementHolder11>();

	public void addAdvancment(AdvancementHolder11 api)
	{
		NamespacedKey key = api.getID();

		for(AdvancementHolder11 adAPI : this.holders)
		{
			if(adAPI.getID().toString().equalsIgnoreCase(key.toString()))
			{
				return;
			}
		}

		this.holders.add(api);
	}

	public static enum AdvancementBackground
	{
		ADVENTURE("minecraft:textures/gui/advancements/backgrounds/adventure.png"),
		END("minecraft:textures/gui/advancements/backgrounds/end.png"),
		HUSBANDRY("minecraft:textures/gui/advancements/backgrounds/husbandry.png"),
		NETHER("minecraft:textures/gui/advancements/backgrounds/nether.png"),
		STONE("minecraft:textures/gui/advancements/backgrounds/stone.png"),
		fromNamespace(null);

		public String str;

		AdvancementBackground(String str)
		{
			this.str = str;
		}

		public void fromNamespace(String string)
		{
			str = string;
		}
	}

	public AdvancementHolder11(String id)
	{
		this.id = new NamespacedKey(PrimalPlugin.instance, "story/" + id);
		this.items = Lists.newArrayList();
		this.announce = true;
	}

	public NamespacedKey getID()
	{
		return id;
	}

	public String getIcon()
	{
		return icon;
	}

	public AdvancementHolder11 withIcon(String icon)
	{
		this.icon = icon;
		return this;
	}

	public AdvancementHolder11 withIcon(Material material)
	{
		this.icon = getMinecraftIDFrom(new ItemStack(material));
		return this;
	}

	@SuppressWarnings("deprecation")
	public AdvancementHolder11 withIcon(MaterialData material)
	{
		this.icon = getMinecraftIDFrom(new ItemStack(material.getItemType()));
		this.subID = (int) material.getData();
		return this;
	}

	public AdvancementHolder11 withIconData(int subID)
	{
		this.subID = subID;
		return this;
	}

	public String getDescription()
	{
		return description;
	}

	public AdvancementHolder11 withDescription(String description)
	{
		this.description = description;
		return this;
	}

	public String getBackground()
	{
		return background;
	}

	public AdvancementHolder11 withBackground(String url)
	{
		this.background = url;
		return this;
	}

	public AdvancementHolder11 withAmount(int i)
	{
		this.amount = i;
		return this;
	}

	public String getTitle()
	{
		return title;
	}

	public AdvancementHolder11 withTitle(String title)
	{
		this.title = title;
		return this;
	}

	public String getParent()
	{
		return parent;
	}

	public AdvancementHolder11 withParent(String parent)
	{
		this.parent = parent;
		return this;
	}

	public AdvancementHolder11 withToast(boolean bool)
	{
		this.toast = bool;
		return this;
	}

	public String getTrigger()
	{
		return trigger;
	}

	public AdvancementHolder11 withTrigger(String trigger)
	{
		this.trigger = trigger;
		return this;
	}

	public List<ItemStack> getItems()
	{
		return items;
	}

	public AdvancementHolder11 withItem(ItemStack is)
	{
		items.add(is);
		return this;
	}

	public String getFrame()
	{
		return frame;
	}

	public AdvancementHolder11 withFrame(FrameType frame)
	{
		this.frame = frame.getName();
		return this;
	}

	public boolean getAnnouncement()
	{
		return announce;
	}

	public AdvancementHolder11 withAnnouncement(boolean announce)
	{
		this.announce = announce;
		return this;
	}

	@SuppressWarnings("unchecked")
	public String getJSON()
	{
		if(this.amount > 0)
		{
			return getJson(this.amount);
		}
		JSONObject json = new JSONObject();

		JSONObject icon = new JSONObject();
		icon.put("item", getIcon());
		icon.put("data", getIconSubID());

		JSONObject display = new JSONObject();
		display.put("icon", icon);
		display.put("title", getTitle());
		display.put("description", getDescription());
		display.put("background", getBackground());
		display.put("frame", getFrame());
		display.put("announce_to_chat", getAnnouncement());
		display.put("show_toast", getToast());

		json.put("parent", getParent());

		JSONObject criteria = new JSONObject();
		JSONObject conditions = new JSONObject();
		JSONObject elytra = new JSONObject();

		JSONArray itemArray = new JSONArray();
		JSONObject itemJSON = new JSONObject();

		for(ItemStack i : getItems())
		{
			itemJSON.put("item", "minecraft:" + i.getType().name().toLowerCase());
			itemJSON.put("amount", i.getAmount());
			itemArray.add(itemJSON);
		}

		conditions.put("items", itemArray);
		elytra.put("trigger", getTrigger());
		elytra.put("conditions", conditions);

		criteria.put("elytra", elytra);

		json.put("criteria", criteria);
		json.put("display", display);

		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String prettyJson = gson.toJson(json);

		return prettyJson;
	}

	@SuppressWarnings("unchecked")
	public String getJson(int amaunt)
	{
		if(!getFrame().equalsIgnoreCase("challenge"))
		{
			return getJSON();
		}
		JSONObject json = new JSONObject();

		JSONObject icon = new JSONObject();
		icon.put("item", getIcon());
		icon.put("data", getIconSubID());

		JSONObject display = new JSONObject();
		display.put("icon", icon);
		display.put("title", getTitle());
		display.put("description", getDescription());
		display.put("background", getBackground());
		display.put("frame", getFrame());
		display.put("announce_to_chat", getAnnouncement());
		display.put("show_toast", getToast());

		json.put("parent", getParent());

		JSONObject criteria = new JSONObject();
		JSONObject conditions = new JSONObject();

		JSONArray itemArray = new JSONArray();
		JSONObject itemJSON = new JSONObject();

		for(ItemStack i : getItems())
		{
			itemJSON.put("item", "minecraft:" + i.getType().name().toLowerCase());
			itemJSON.put("amount", i.getAmount());
			itemArray.add(itemJSON);
		}

		for(int i = 0; i <= amaunt; i++)
		{
			JSONObject elytra = new JSONObject();
			elytra.put("trigger", "minecraft:impossible");
			conditions.put("items", itemArray);
			elytra.put("conditions", conditions);
			criteria.put("key" + i, elytra);
		}

		json.put("criteria", criteria);
		json.put("display", display);

		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String prettyJson = gson.toJson(json);

		return prettyJson;
	}

	private boolean getToast()
	{
		return this.toast;
	}

	private int getIconSubID()
	{
		return this.subID;
	}

	@SuppressWarnings("deprecation")
	public void loadAdvancement()
	{
		for(World world : Bukkit.getWorlds())
		{
			Path path = Paths.get(world.getWorldFolder() + File.separator + "data" + File.separator + "advancements" + File.separator + id.getNamespace() + File.separator + getID().getKey().split("/")[0]);

			Path path2 = Paths.get(world.getWorldFolder() + File.separator + "data" + File.separator + "advancements" + File.separator + id.getNamespace() + File.separator + getID().getKey().split("/")[0] + File.separator + getID().getKey().split("/")[1] + ".json");

			if(!path.toFile().exists())
			{
				path.toFile().mkdirs();
			}

			if(!path2.toFile().exists())
			{
				File file = path2.toFile();
				try
				{
					file.createNewFile();
					FileWriter writer = new FileWriter(file);
					writer.write(getJSON());
					writer.flush();
					writer.close();
				}
				catch(IOException e)
				{
					e.printStackTrace();
				}
			}
		}

		if(Bukkit.getAdvancement(getID()) == null)
		{
			Bukkit.getUnsafe().loadAdvancement(getID(), getJSON());
		}

		addAdvancment(this);
	}

	@SuppressWarnings("deprecation")
	public void delete()
	{
		Bukkit.getUnsafe().removeAdvancement(getID());
	}

	@SuppressWarnings("deprecation")
	public void delete(Player... player)
	{
		for(Player p : player)
		{
			if(p.getAdvancementProgress(getAdvancement()).isDone())
			{
				p.getAdvancementProgress(getAdvancement()).revokeCriteria("elytra");
			}
		}

		Bukkit.getScheduler().scheduleSyncDelayedTask(PrimalPlugin.instance, new Runnable()
		{
			@Override
			public void run()
			{
				CraftMagicNumbers.INSTANCE.removeAdvancement(getID());
			}
		}, 5);
	}

	public static String getMinecraftIDFrom(ItemStack stack)
	{
		final int check = Item.getId(CraftItemStack.asNMSCopy(stack).getItem());
		final MinecraftKey matching = Item.REGISTRY.keySet().stream().filter(key -> Item.getId(Item.REGISTRY.get(key)) == check).findFirst().orElse(null);
		return Objects.toString(matching, null);
	}

	public void sendPlayer(Player... player)
	{
		for(Player p : player)
		{
			if(!p.getAdvancementProgress(getAdvancement()).isDone())
			{
				p.getAdvancementProgress(getAdvancement()).awardCriteria("elytra");
			}
		}
	}

	public void sendPlayer(String criteria, Player... player)
	{
		for(Player p : player)
		{
			if(!p.getAdvancementProgress(getAdvancement()).isDone())
			{
				p.getAdvancementProgress(getAdvancement()).awardCriteria(criteria);
			}
		}
	}

	public boolean next(Player p)
	{
		if(!p.getAdvancementProgress(getAdvancement()).isDone())
		{
			for(String criteria : getAdvancement().getCriteria())
			{
				if(p.getAdvancementProgress(getAdvancement()).getDateAwarded(criteria) == null)
				{
					p.getAdvancementProgress(getAdvancement()).awardCriteria(criteria);
					return true;
				}
			}
		}
		return false;
	}

	public boolean next(Player p, long diff, boolean onlyLast)
	{
		if(!p.getAdvancementProgress(getAdvancement()).isDone())
		{
			Date oldData = null;
			String str = "";
			for(String criteria : getAdvancement().getCriteria())
			{
				if(p.getAdvancementProgress(getAdvancement()).getDateAwarded(criteria) != null)
				{
					oldData = p.getAdvancementProgress(getAdvancement()).getDateAwarded(criteria);
					str = criteria;
					continue;
				}
				else
				{
					if(oldData == null)
					{
						p.getAdvancementProgress(getAdvancement()).awardCriteria(criteria);
						return true;
					}
					else
					{
						long oldTime = oldData.getTime();
						long current = System.currentTimeMillis();
						if((current - diff) > oldTime)
						{
							if(onlyLast)
							{
								p.getAdvancementProgress(getAdvancement()).revokeCriteria(str);
								return false;
							}
							else
							{
								for(String string : getAdvancement().getCriteria())
								{
									p.getAdvancementProgress(getAdvancement()).revokeCriteria(string);
								}
								p.getAdvancementProgress(getAdvancement()).awardCriteria(getAdvancement().getCriteria().stream().findFirst().get());
								return false;
							}
						}
						else
						{
							p.getAdvancementProgress(getAdvancement()).awardCriteria(criteria);
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	public Date getLastAwardTime(Player p)
	{
		if(!p.getAdvancementProgress(getAdvancement()).isDone())
		{
			Date oldData = null;
			for(String criteria : getAdvancement().getCriteria())
			{
				if(p.getAdvancementProgress(getAdvancement()).getDateAwarded(criteria) != null)
				{
					oldData = p.getAdvancementProgress(getAdvancement()).getDateAwarded(criteria);
					continue;
				}
				else
				{
					return oldData;
				}
			}
		}
		return null;
	}

	public Advancement getAdvancement()
	{
		return Bukkit.getAdvancement(getID());
	}
}