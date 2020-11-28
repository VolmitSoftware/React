package primal.bukkit.nms;

public enum FrameType
{
	CHALLANGE("challenge"),
	GOAL("goal"),
	TASK("task");

	private String str;

	FrameType(String str)
	{
		this.str = str;
	}

	public String getName()
	{
		return this.str;
	}
}