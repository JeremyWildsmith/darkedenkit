package io.github.jeremywildsmith.darkedenkit.dumper.pk;

import io.github.jeremywildsmith.darkedenkit.ConstInputStream;
import io.github.jeremywildsmith.darkedenkit.IOUtils;
import io.github.jeremywildsmith.darkedenkit.LittleEndianDataInputStream;
import io.github.jeremywildsmith.darkedenkit.NullInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;

public final class PkCompressedStream extends InputStream
{
	private final byte[] m_fill;
	private final boolean[] m_copyFillPattern;
	private final LittleEndianDataInputStream m_pkSource;
	
	private InputStream m_lastChunkStream = new NullInputStream();
	
	private int m_operationIndex = 0;
	
	public PkCompressedStream(byte[] fill, boolean[] copyFillPattern, InputStream pkSource)
	{
		if(copyFillPattern.length == 0)
			throw new IllegalArgumentException("Copy fill pattern must contain at least one element");

		m_fill = fill;
		m_copyFillPattern = copyFillPattern;
		m_pkSource = new LittleEndianDataInputStream(pkSource);
	}
	
	//Since the streams are provided by the caller, it is their responsibility to close them.
	private InputStream extract(int length) throws IOException
	{
		if(m_pkSource.available() == 0)
			return new NullInputStream();
		
		InputStream extractSource = new NullInputStream();
		
		try
		{
			int extracted = 0;
			
			while(extracted < length && m_pkSource.available() != 0)
			{
				int operationLength = m_pkSource.readShort() * 2;
				extracted += operationLength;
				
				if(m_copyFillPattern[m_operationIndex % m_copyFillPattern.length])
					extractSource = new SequenceInputStream(extractSource, IOUtils.copy(m_pkSource, operationLength));
				else
					extractSource = new SequenceInputStream(extractSource, new ConstInputStream(m_fill, operationLength));
				
				m_operationIndex++;
			}
			
			return extractSource;
		} catch (IOException e)
		{
			throw new IOException("IO error occured attempting to decompress PK chunk.", e);
		}
	}

	private InputStream extract() throws IOException
	{
		return extract(1);
	}
	
	@Override
	public int available() throws IOException
	{
		return m_lastChunkStream.available() + m_pkSource.available();
	}
	
	@Override
	public int read() throws IOException
	{
		if(m_lastChunkStream.available() == 0 && m_pkSource.available() != 0)
		{
			m_lastChunkStream = extract();
			return read();
		}
		else
			return m_lastChunkStream.read();
	}
	
	@Override
	public int read(byte[] buf) throws IOException
	{
		return read(buf, 0, buf.length);
	}
	
	@Override
	public int read(byte buf[], int off, int len) throws IOException
	{
		int read = IOUtils.copy(m_lastChunkStream, buf, off, len);
		
		if(read != len)
			m_lastChunkStream = extract(len - read);
		
		read += IOUtils.copy(m_lastChunkStream, buf, off + read, len - read);
		
		return read == 0 && m_lastChunkStream.available() == 0 ? -1 : read;
	}
	
	@Override
	public void close() throws IOException
	{
		m_pkSource.close();
	}
}
