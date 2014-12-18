package io.github.jeremywildsmith.darkedenkit.dumper;

import io.github.jeremywildsmith.darkedenkit.dumper.pk.PkSpriteExtractor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpkDumper extends PkDumper
{
	private static final Logger m_logger = LoggerFactory.getLogger(SpkDumper.class);
	private static final String OUTPUT_IMAGE_FORMAT = "png";
	
	private final PkSpriteExtractor m_extractor;
	
	public SpkDumper(File sourceSpkFile, File sourceSpkiFile, URI destinationDirectory, boolean[] spkCopyFillPattern)
	{
		super(sourceSpkFile, sourceSpkiFile, destinationDirectory);
		m_extractor = new PkSpriteExtractor(OUTPUT_IMAGE_FORMAT, spkCopyFillPattern);
	}
	
	public static void main(String[] args)
	{
		m_logger.info("[spk source] [spki source] [destination directory] [copy fill pattern = 01 for spk, = 011 for ispk]");
		
		if(args.length < 4)
			m_logger.error("Insufficient arguments provided to run spk dump operation");
		else
		{
			String copyFillPattern = args[3];
			
			if(copyFillPattern.isEmpty())
				m_logger.error("Invalid copy fill pattern supplied.");
			else
			{
				boolean copyFillPatternBuffer[] = new boolean[copyFillPattern.length()];
				
				for(int i = 0; i < copyFillPattern.length(); i++)
					copyFillPatternBuffer[i] = copyFillPattern.charAt(i) == '1';
				
				File f = new File(args[2]);
				if(f.exists() && f.isDirectory())
					new SpkDumper(new File(args[0]), new File(args[1]), f.toURI(), copyFillPatternBuffer).run();
				else
					m_logger.error("Destination argument must be a directory that already exists. Operation aborted. The provided destination argument was not valid.");
			}
		}
	}

	@Override
	protected String generateArtifactName(int origin, int pkiIndex)
	{
		return String.format("texture_%d_%08X.%s", pkiIndex, origin, OUTPUT_IMAGE_FORMAT);
	}

	@Override
	protected void extract(InputStream artifactSource, OutputStream dest) throws IOException
	{
		m_extractor.extract(artifactSource, dest);
	}
}
