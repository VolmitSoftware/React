package primal.logic.io;

import java.io.IOException;

public class DownloadException extends IOException
{
	private static final long serialVersionUID = 5137918663903349839L;

	public DownloadException() {
		super();
	}

	public DownloadException(String message, Throwable cause) {
		super(message, cause);
	}

	public DownloadException(String message) {
		super(message);
	}

	public DownloadException(Throwable cause) {
		super(cause);
	}
}
