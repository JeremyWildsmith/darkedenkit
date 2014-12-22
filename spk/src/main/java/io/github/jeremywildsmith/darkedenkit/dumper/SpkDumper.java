package io.github.jeremywildsmith.darkedenkit.dumper;

import io.github.jeremywildsmith.darkedenkit.dumper.pk.PkIndexExtractor;
import io.github.jeremywildsmith.darkedenkit.dumper.pk.PkIndexExtractor.PkiParseException;
import io.github.jeremywildsmith.darkedenkit.dumper.pk.PkSpriteExtractor;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.channels.Channels;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SpkDumper implements Runnable
{
	private static final String OUTPUT_IMAGE_FORMAT = "png";
	
	private static final Logger m_logger = LoggerFactory.getLogger(SpkDumper.class);

	private final File m_sourcePkFile;
	private final File m_sourcePkiFile;
	private final URI m_destinationDirectory;
	private final PkSpriteExtractor m_extractor;

	public SpkDumper(File sourcePkFile, File sourcePkiFile, URI destinationDirectory, boolean[] spkCopyFillPattern)
	{
		m_sourcePkFile = sourcePkFile;
		m_sourcePkiFile = sourcePkiFile;
		m_destinationDirectory = destinationDirectory;

		m_extractor = new PkSpriteExtractor(spkCopyFillPattern);
	}
	
	@Override
	public final void run()
	{
		long pkIndices[] = new long[0];
		
		try(FileInputStream spkiFis = new FileInputStream(m_sourcePkiFile))
		{
			pkIndices = new PkIndexExtractor().extract(spkiFis);
		} catch (IOException | PkiParseException e)
		{
			m_logger.error("Unable to extract spki indices, assuming no indexes.", e);
		}
		
		if(pkIndices.length == 0)
			m_logger.info("No spk graphic indices associated to respective SPK document. There is nothing to extract.");
		else
		{
			try(RandomAccessFile spk = new RandomAccessFile(m_sourcePkFile, "r"))
			{
				for(int i = 0; i < pkIndices.length; i++)
				{
					spk.getChannel().position(pkIndices[i]);
					
					try
					{
						BufferedImage src = extract(Channels.newInputStream(spk.getChannel()));
						if(src != null)
						{
							if(!ImageIO.write(src, OUTPUT_IMAGE_FORMAT, new File(m_destinationDirectory.resolve(String.format("./%s", generateArtifactName(i, pkIndices[i]))))))
							{
								m_logger.error("Unable to find approriate writer for " + OUTPUT_IMAGE_FORMAT + ". Terminating.");
								break;
							}
						}
					} catch (IOException e)
					{
						m_logger.error(String.format("Unable to extract artifact at index %d at origin %d. Skipping artifact.", i, pkIndices[i]), e);
					}
				}
			} catch (IOException e)
			{
				m_logger.error("Error encountered extracting graphic images. Terminating extraction operation.", e);
			}
		}
		
		m_logger.info("Done");
	}

	private String generateArtifactName(int pkiIndex, long origin)
	{
		return String.format("texture_%d_%08X.%s", pkiIndex, origin, OUTPUT_IMAGE_FORMAT);
	}

	private BufferedImage extract(InputStream artifactSource) throws IOException
	{
		return m_extractor.extract(artifactSource);
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
}
