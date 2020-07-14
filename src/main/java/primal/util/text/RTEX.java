package primal.util.text;

import primal.lang.collection.GList;
import primal.lang.json.JSONArray;
import primal.lang.json.JSONObject;

/**
 * Raw Text EXTRA
 *
 * @author cyberpwn
 */
public class RTEX
{
	private GList<ColoredString> extras;

	/**
	 * Create a new raw text base
	 *
	 * @param extras
	 *            the extras
	 */
	public RTEX(ColoredString... extras)
	{
		this.extras = new GList<ColoredString>(extras);
	}

	public RTEX()
	{
		this.extras = new GList<ColoredString>();
	}

	public GList<ColoredString> getExtras()
	{
		return extras;
	}

	/**
	 * Get the json object for this
	 *
	 * @return
	 */
	public JSONObject toJSON()
	{
		JSONObject js = new JSONObject();
		JSONArray jsa = new JSONArray();

		for(ColoredString i : extras)
		{
			JSONObject extra = new JSONObject();
			extra.put("text", i.getS());
			extra.put("color", i.getC().name().toLowerCase());
			jsa.put(extra);
		}

		js.put("text", "");
		js.put("extra", jsa);

		return js;
	}
}
