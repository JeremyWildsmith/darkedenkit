package io.github.jeremywildsmith.darkedenkit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class IOUtils
{
	public static InputStream copy(InputStream source, int length) throws IOException
	{
		ByteArrayOutputStream bos = new ByteArrayOutputStream(length);
		
		if(copy(source, bos, length) != length)
			throw new EOFException();
		
		return new ByteArrayInputStream(bos.toByteArray());
	}
	
	public static int copy(InputStream source, OutputStream dest, int length) throws IOException
	{
		byte buffer[] = new byte[1024 * 4];

		int totalRead = 0;
		for(int read = 0; 
				totalRead < length && (read = source.read(buffer, 0, Math.min(buffer.length, length - totalRead))) != -1;
				totalRead += read, dest.write(buffer, 0, read));
		
		return totalRead;
	}
	
	public static int copy(InputStream source, OutputStream dest) throws IOException
	{
		byte buffer[] = new byte[1024 * 4];

		int totalRead = 0;
		for(int read = 0; 
				(read = source.read(buffer, 0, buffer.length)) != -1;
				totalRead += read, dest.write(buffer, 0, read));
		
		return totalRead;
	}
	
	public static int copy(InputStream source, byte[] dest, int offset, int length) throws IOException
	{
		int totalRead = 0;
		for(int read = 0; 
				totalRead < length && (read = source.read(dest, offset + totalRead, Math.min(dest.length - offset, length - totalRead))) != -1;
				totalRead += read);
		
		return totalRead;		
	}
}
