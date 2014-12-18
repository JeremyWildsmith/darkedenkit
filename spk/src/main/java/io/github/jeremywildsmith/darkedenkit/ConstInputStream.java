package io.github.jeremywildsmith.darkedenkit;

import java.io.IOException;
import java.io.InputStream;

public final class ConstInputStream extends InputStream
{
	private final byte[] m_pattern;
	private final int m_length;
	
	private int m_read = 0;

	public ConstInputStream(byte[] pattern, int length)
	{
		m_pattern = pattern;
		m_length = length;
	}
	
	@Override
	public int read() throws IOException
	{
		if(m_read >= m_length)
			return -1;
		
		int read = m_pattern[m_read % m_pattern.length];
		m_read++;
		
		return read;
	}

	@Override
	public int read(byte[] buf) throws IOException
	{
		return read(buf, 0, buf.length);
	}
	
	@Override
	public int read(byte[] buf, int off, int len)
	{
		if(m_length - m_read <= 0)
			return -1;
		
		int maxRead = Math.min(m_length - m_read, len);
		
		for(int i = 0; i < maxRead; i++)
			buf[off + i] = m_pattern[(m_read + i) % m_pattern.length];
		
		m_read += maxRead;
		
		return maxRead;
	}
	
	@Override
	public int available()
	{
		return m_length - m_read;
	}
}
