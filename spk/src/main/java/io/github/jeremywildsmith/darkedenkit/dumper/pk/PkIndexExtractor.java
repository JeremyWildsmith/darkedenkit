package io.github.jeremywildsmith.darkedenkit.dumper.pk;

import io.github.jeremywildsmith.darkedenkit.LittleEndianDataInputStream;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class PkIndexExtractor
{
	public long[] extract(InputStream spkiSource) throws PkiParseException
	{
		//Since the caller provided the input stream, it is their responsibility to close it.
		@SuppressWarnings("resource")
		LittleEndianDataInputStream dis = new LittleEndianDataInputStream(spkiSource);

		int indexCount = 0;
		try
		{
			indexCount = dis.readUnsignedShort();
		} catch (IOException e)
		{
			throw new PkiParseException("Error occured while attempting to parse number of total indice in file.", e);
		}
		
		if(indexCount <= 0)
			return new long[0];
		
		long indice[] = new long[indexCount];
		
		try
		{
			for(int i = 0; i < indexCount; i++)
				indice[i] = dis.readUnsignedInt();
		} catch(EOFException e)
		{
			throw new PkiParseException("Unexpected eof before all indices could be read from file", e);
		} catch(IOException e)
		{
			throw new PkiParseException("IO error occured attempting to read spki file contents.", e);
		}
		
		return indice;
	}
	
	public static final class PkiParseException extends Exception
	{
		private static final long serialVersionUID = 1L;
	
		private PkiParseException(String message, Exception cause)
		{
			super(cause);
		}
		
		private PkiParseException(String message)
		{
			super(message);
		}
	}
}
