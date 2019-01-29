package com.volmit.react.api;

import com.volmit.react.util.Persist;

@Persist
public class DTest
{
	@Persist
	public static int test()
	{
		int score = 0;
		long st = System.currentTimeMillis();
		double tx = 0;
		double tc = 0;
		double td = 0;
		while(System.currentTimeMillis() - st < 1000)
		{
			SimplexNoiseGenerator simplex = new SimplexNoiseGenerator(1234L);
			PerlinNoiseGenerator perlin = new PerlinNoiseGenerator(99999999L);
			tx = 0;
			tc = 0;
			td = 0;

			for(int ix = 0; ix < 1000; ix++)
			{
				long ff = System.nanoTime();
				double a = ix + 10000 - 401 + Math.PI + 400 / Math.E;
				double b = Math.sqrt(4 * a - 2);
				double c = Math.cbrt(b + a - 4);
				double d = Math.PI / c;
				double e = Math.E * a;
				double f = Math.pow(a, b) + Math.cbrt(e - d);
				double g = Math.pow(a + b - c + d - e + f, 0.02521);
				double h = Math.pow(a + g - c / d + f - e, 0.116342);
				double i = Math.sin(Math.cos(Math.cosh(h - g + a + b) + d + e) - f) + g;
				double j = Math.tan(Math.acos(a - f + i) + d + Math.atan(4 + g));
				double k = Math.cbrt(j + g - i + j / 4 * d + Math.sin(c));
				double l = Math.atan2(j, 3);
				Math.expm1(l + k - j + f * e);
				tx += (double) (System.nanoTime() - ff) / 1000000.0;
			}

			for(int ix = 0; ix < 1000; ix++)
			{
				long ff = System.nanoTime();
				simplex.noise(12 + (ix * 120), -ix + (ix * 45), ix, ix + 5, 6, 12, false);
				tc += (double) (System.nanoTime() - ff) / 1000000.0;
			}

			for(int ix = 0; ix < 1000; ix++)
			{
				long ff = System.nanoTime();
				perlin.noise(12 + (ix * 120), -ix + (ix * 45), ix, ix + 5, 6, 12, false);
				td += (double) (System.nanoTime() - ff) / 1000000.0;
			}

			int b = (int) (50 - (tx + td + tc));

			score += b + 1;
		}

		return 1100 + (int) (score);
	}
}
