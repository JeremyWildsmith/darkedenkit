package io.github.jeremywildsmith.darkedenkit;

import java.io.IOException;
import java.io.InputStream;

public final class NullInputStream extends InputStream
{

	@Override
	public int read() throws IOException
	{
		return -1;
	}
	
	@Override
	public int read(byte[] buf)
	{
		return -1;
	}
	
	public int read(byte[] buf, int off, int len)
	{
		return -1;
	}
	
	public int available()
	{
		return 0;
	}
}
