package io.github.jeremywildsmith.darkedenkit.dumper;

import io.github.jeremywildsmith.darkedenkit.dumper.pk.PkIndexExtractor;
import io.github.jeremywildsmith.darkedenkit.dumper.pk.PkIndexExtractor.SpkiParseException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.channels.Channels;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PkDumper implements Runnable
{
	private static final Logger m_logger = LoggerFactory.getLogger(SpkDumper.class);

	private final File m_sourcePkFile;
	private final File m_sourcePkiFile;
	private final URI m_destinationDirectory;
	
	public PkDumper(File sourcePkFile, File sourcePkiFile, URI destinationDirectory)
	{
		m_sourcePkFile = sourcePkFile;
		m_sourcePkiFile = sourcePkiFile;
		m_destinationDirectory = destinationDirectory;
	}
	
	@Override
	public final void run()
	{
		int pkIndices[] = new int[0];
		
		try(FileInputStream spkiFis = new FileInputStream(m_sourcePkiFile))
		{
			pkIndices = new PkIndexExtractor().getGraphicIndice(spkiFis);
		} catch (IOException | SpkiParseException e)
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
					
					try(FileOutputStream fos = new FileOutputStream(new File(m_destinationDirectory.resolve(String.format("./%s", generateArtifactName(i, pkIndices[i]))))))
					{
						extract(Channels.newInputStream(spk.getChannel()), fos);
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
	}
	
	protected abstract String generateArtifactName(int orgin, int pkiIndex);
	protected abstract void extract(InputStream artifactSource, OutputStream dest) throws IOException;
}
