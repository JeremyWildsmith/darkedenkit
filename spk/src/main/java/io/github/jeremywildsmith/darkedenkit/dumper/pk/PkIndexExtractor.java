package io.github.jeremywildsmith.darkedenkit.dumper.pk;

import io.github.jeremywildsmith.darkedenkit.LittleEndianDataInputStream;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class PkIndexExtractor
{
	public int[] getGraphicIndice(InputStream spkiSource) throws SpkiParseException
	{
		//Since the caller provided the input stream, it is their responsibility to close it.
		@SuppressWarnings("resource")
		LittleEndianDataInputStream dis = new LittleEndianDataInputStream(spkiSource);

		int indexCount = 0;
		try
		{
			indexCount = dis.readShort();
		} catch (IOException e)
		{
			throw new SpkiParseException("Error occured while attempting to parse number of total indice in file.", e);
		}
		
		if(indexCount <= 0)
			return new int[0];
		
		int indice[] = new int[indexCount];
		
		try
		{
			for(int i = 0; i < indexCount; i++)
				indice[i] = dis.readInt();
		} catch(EOFException e)
		{
			throw new SpkiParseException("Unexpected eof before all indices could be read from file", e);
		} catch(IOException e)
		{
			throw new SpkiParseException("IO error occured attempting to read spki file contents.", e);
		}
		
		return indice;
	}
	
	public static final class SpkiParseException extends Exception
	{
		private static final long serialVersionUID = 1L;
	
		private SpkiParseException(String message, Exception cause)
		{
			super(cause);
		}
		
		private SpkiParseException(String message)
		{
			super(message);
		}
	}
}
