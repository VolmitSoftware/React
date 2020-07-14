package primal.bukkit.nms;

import org.bukkit.Bukkit;

public class PlayerSettings
{
	private final String locale;
	private final int viewDistance;
	private final ChatMode chatMode;
	private final boolean chatColors;
	private final int skinParts;
	private final boolean rightHanded;

	public PlayerSettings(String locale, int viewDistance, ChatMode chatMode, boolean chatColors, int skinParts, boolean rightHanded)
	{
		this.locale = locale;
		this.viewDistance = Math.min(viewDistance, Bukkit.getViewDistance());
		this.chatMode = chatMode;
		this.chatColors = chatColors;
		this.skinParts = skinParts;
		this.rightHanded = rightHanded;
	}

	public String getLocale()
	{
		return locale;
	}

	public int getViewDistance()
	{
		return viewDistance;
	}

	public ChatMode getChatMode()
	{
		return chatMode;
	}

	public boolean isChatColors()
	{
		return chatColors;
	}

	public int getSkinParts()
	{
		return skinParts;
	}

	public boolean isRightHanded()
	{
		return rightHanded;
	}
}
