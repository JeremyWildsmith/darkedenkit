package io.github.jeremywildsmith.darkedenkit;

import java.io.IOException;
import java.io.InputStream;

public final class ReadLimitInputStream extends InputStream
{
	private final InputStream m_source;
	private int m_allowedRead;
	
	public ReadLimitInputStream(InputStream source, int limit)
	{
		m_source = source;
		m_allowedRead = limit;
	}
	
	@Override
	public int read() throws IOException
	{
		if(m_allowedRead <= 0)
			return -1;
		
		int read = m_source.read();

		if(read >= 0)
			m_allowedRead--;
	
		return read;
	}
	
	@Override
	public int read(byte[] buf) throws IOException
	{
		return read(buf, 0, m_allowedRead);
	}
	
	@Override
	public int read(byte[] buf, int offset, int amount) throws IOException
	{
		if(m_allowedRead <= 0)
			return -1;
		
		int read = m_source.read(buf, offset, Math.min(amount, m_allowedRead));
		
		if(read > 0)
			m_allowedRead -= read;
		
		return read;
	}
	
	@Override
	public void close() throws IOException
	{
		m_source.close();
	}
	
	@Override
	public int available() throws IOException
	{
		return Math.max(0, Math.min(m_allowedRead, m_source.available()));
	}
}
