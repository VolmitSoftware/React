package com.volmit.react.api;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;

import org.bukkit.map.MapFont;
import org.bukkit.map.MapFont.CharacterSprite;

import com.volmit.react.util.M;

public class BufferedFrame
{
	private byte[][] frame;
	private int width;
	private int height;

	public BufferedFrame()
	{
		this(128, 128);
	}

	public BufferedFrame(int w, int h)
	{
		width = w;
		height = h;
		frame = new byte[w][h];
		write(FrameColor.TRANSPARENT);
	}

	public void drawText(int x, int y, MapFont font, byte ccc, String text)
	{
		int xStart = x;
		byte color = ccc;
		if(!font.isValid(text))
		{
			throw new IllegalArgumentException("text contains invalid characters");
		}

		for(int i = 0; i < text.length(); ++i)
		{
			char ch = text.charAt(i);

			if(ch == '\n')
			{
				x = xStart;
				y += font.getHeight() + 1;
				continue;
			}

			else if(ch == '\u00A7')
			{
				int j = text.indexOf(';', i);
				if(j >= 0)
				{
					try
					{
						color = Byte.parseByte(text.substring(i + 1, j));
						i = j;
						continue;
					}
					catch(NumberFormatException ex)
					{

					}
				}
			}

			CharacterSprite sprite = font.getChar(text.charAt(i));

			for(int r = 0; r < font.getHeight(); ++r)
			{
				for(int c = 0; c < sprite.getWidth(); ++c)
				{
					if(sprite.get(r, c))
					{
						write(x + c, y + r, color);
					}
				}
			}

			x += sprite.getWidth() + 1;
		}
	}

	public void write(byte c)
	{
		int i;
		int j;

		for(i = 0; i < width; i++)
		{
			for(j = 0; j < height; j++)
			{
				write(i, j, c);
			}
		}
	}

	public BufferedFrame scale(double x, double y, int affineTransformOp)
	{
		BufferedImage before = toBufferedImage();
		int w = before.getWidth();
		int h = before.getHeight();
		BufferedImage after = new BufferedImage((int) (w * x), (int) (h * y), BufferedImage.TYPE_INT_ARGB);
		AffineTransform at = new AffineTransform();
		at.scale(x, y);
		AffineTransformOp scaleOp = new AffineTransformOp(at, affineTransformOp);
		after = scaleOp.filter(before, after);
		BufferedFrame bf = new BufferedFrame(after.getWidth(), after.getHeight());
		bf.fromBufferedImage(after);

		return bf;
	}

	public void fromBufferedImage(BufferedImage bu)
	{
		int i;
		int j;

		for(i = 0; i < width; i++)
		{
			for(j = 0; j < height; j++)
			{
				write(i, j, FrameColor.matchColor(new Color(bu.getRGB(i, j), true)));
			}
		}
	}

	public void fromBufferedImage(BufferedImage bu, int xs, int ys)
	{
		int i;
		int j;

		for(i = 0; i < width; i++)
		{
			for(j = 0; j < height; j++)
			{
				try
				{
					write(i, j, FrameColor.matchColor(new Color(bu.getRGB(i + xs, j + ys), true)));
				}

				catch(Exception e)
				{

				}
			}
		}
	}

	public BufferedImage toBufferedImage()
	{
		BufferedImage bu = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);

		int i;
		int j;

		for(i = 0; i < width; i++)
		{
			for(j = 0; j < height; j++)
			{
				Color a = FrameColor.getColor(frame[i][j]);
				bu.setRGB(i, j, new Color(a.getRed(), a.getGreen(), a.getBlue(), a.getAlpha()).getRGB());
			}
		}

		return bu;
	}

	public void writeRainbowMul()
	{
		int i;
		int j;

		for(i = 0; i < width; i++)
		{
			for(j = 0; j < height; j++)
			{
				write(i, j, FrameColor.matchColor(Color.getHSBColor((float) (i * j) / (float) (width * height), 1.0f, 1.0f)));
			}
		}
	}

	public void writeRainbowAdd()
	{
		int i;
		int j;

		for(i = 0; i < width; i++)
		{
			for(j = 0; j < height; j++)
			{
				write(i, j, FrameColor.matchColor(Color.getHSBColor((float) (i + j) / (float) (width + height), 1.0f, 1f)));
			}
		}
	}

	public void writeSparks()
	{
		int i;
		int j;

		for(i = 0; i < width; i++)
		{
			for(j = 0; j < height; j++)
			{
				if(M.r(0.05))
				{
					write(i, j, FrameColor.WHITE);
				}
			}
		}
	}

	public boolean write(int x, int y, byte c)
	{
		if((int) M.clip(x, 0, width - 1) != x || (int) M.clip(y, 0, height - 1) != y)
		{
			return false;
		}

		frame[(int) M.clip(x, 0, width - 1)][(int) M.clip(y, 0, height - 1)] = c;
		return true;
	}

	public int write(BufferedFrame frame, int sx, int sy)
	{
		int wrote = 0;
		byte[][] pframe = frame.getRawFrame();

		int i;
		int j;

		for(i = 0; i < M.min(frame.getWidth(), getWidth()); i++)
		{
			for(j = 0; j < M.min(frame.getHeight(), getHeight()); j++)
			{
				if(pframe[i][j] == 0)
				{
					continue;
				}

				if(write(i + sx, j + sy, pframe[i][j]))
				{
					wrote++;
				}
			}
		}

		return wrote;
	}

	public void write(BufferedFrame frame)
	{
		byte[][] pframe = frame.getRawFrame();

		int i;
		int j;

		for(i = 0; i < M.min(frame.getWidth(), getWidth()); i++)
		{
			for(j = 0; j < M.min(frame.getHeight(), getHeight()); j++)
			{
				if(pframe[i][j] == 0)
				{
					continue;
				}

				write(i, j, pframe[i][j]);
			}
		}
	}

	public byte[][] getRawFrame()
	{
		return frame;
	}

	public int getWidth()
	{
		return width;
	}

	public int getHeight()
	{
		return height;
	}
}
