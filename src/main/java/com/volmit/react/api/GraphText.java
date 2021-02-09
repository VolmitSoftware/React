package com.volmit.react.api;

import java.awt.image.AffineTransformOp;

import com.volmit.react.util.M;

public class GraphText extends NormalGraph implements IGraph
{
	private String text;
	private byte fontColor;
	private byte backgroundColor;
	private boolean wrote;
	private long lastf = M.ms();
	private BufferedFrame last;

	public GraphText(String textInitial, byte fontColor)
	{
		super("textedGraph-" + textInitial, 1000);

		wrote = false;
		this.text = textInitial;
		this.fontColor = fontColor;
		this.backgroundColor = FrameColor.matchColor(FrameColor.getColor(fontColor).darker().darker());
	}

	@Override
	public void onRender(BufferedFrame frame)
	{
		if(M.ms() - lastf < 750.0)
		{
			if(last != null)
			{
				frame.write(last);
				return;
			}
		}

		lastf = M.ms();

		if(!wrote)
		{
			int x = (frame.getWidth() / 2);
			int y = (frame.getHeight() / 2);
			int w = ReactFont.Font.getWidth(text) + 6;
			int h = ReactFont.Font.getHeight() + 6;
			BufferedFrame presc = new BufferedFrame(w, h);
			presc.write(backgroundColor);
			presc.drawText(3, 3, ReactFont.Font, FrameColor.matchColor(0, 0, 0), text);
			presc.drawText(2, 2, ReactFont.Font, fontColor, text);
			presc = presc.scale(2, 2, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
			presc = presc.scale(1.5, 1.5, AffineTransformOp.TYPE_BILINEAR);
			w = presc.getWidth();
			h = presc.getHeight();
			frame.write(backgroundColor);
			frame.write(presc, x - (w / 2), y - (h / 2));
			wrote = true;
			last = frame;
		}
	}

	public String getText()
	{
		return text;
	}

	public void setText(String text)
	{
		this.text = text;
	}

	public byte getFontColor()
	{
		return fontColor;
	}

	public void setFontColor(byte fontColor)
	{
		this.fontColor = fontColor;
	}

	public byte getBackgroundColor()
	{
		return backgroundColor;
	}

	public void setBackgroundColor(byte backgroundColor)
	{
		this.backgroundColor = backgroundColor;
	}
}
