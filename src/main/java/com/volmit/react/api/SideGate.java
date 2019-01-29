package com.volmit.react.api;

public enum SideGate
{
	PLAYERS_ONLY,
	CONSOLES_ONLY,
	ANYTHING;

	public boolean supports(Side side)
	{
		if(side.equals(Side.CONSOLE))
		{
			switch(this)
			{
				case ANYTHING:
					return true;
				case CONSOLES_ONLY:
					return true;
				case PLAYERS_ONLY:
					return false;
			}
		}

		switch(this)
		{
			case ANYTHING:
				return true;
			case CONSOLES_ONLY:
				return false;
			case PLAYERS_ONLY:
				return true;
		}

		return false;
	}
}
