package primal.logic.io;

import java.io.InputStream;

@FunctionalInterface
public interface InputHandler
{
	void read(InputStream in);
}
