package io.github.jeremywildsmith.darkedenkit.spk;

import io.github.jeremywildsmith.darkedenkit.spk.SpkGraphicExtractor.SpkGraphicParseException;
import io.github.jeremywildsmith.darkedenkit.spk.SpkiIndexExtractor.SpkiParseException;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
import java.awt.image.RGBImageFilter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.channels.Channels;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpkDumper implements Runnable
{
	private static final Logger m_logger = LoggerFactory.getLogger(SpkDumper.class);

	private final String m_sourceSpkFile;
	private final String m_sourceSpkiFile;
	private final URI m_destinationDirectory;
	private final boolean[] m_spkCopyFillPattern;
	
	public SpkDumper(String sourceSpkFile, String sourceSpkiFile, URI destinationDirectory, boolean[] spkCopyFillPattern)
	{
		m_sourceSpkFile = sourceSpkFile;
		m_sourceSpkiFile = sourceSpkiFile;
		m_destinationDirectory = destinationDirectory;
		m_spkCopyFillPattern = spkCopyFillPattern;
	}
	
	private BufferedImage filterWhiteToTransparent(Image img)
	{		
		ImageFilter filter = new RGBImageFilter() {
			@Override
			public int filterRGB(int x, int y, int rgb)
			{
				return (rgb & 0x00FFFFFF) == 0x00FFFFFF ? 0x00FFFFFF : rgb;
			}
		};
		
		Image transparentImage = Toolkit.getDefaultToolkit().createImage(new FilteredImageSource(img.getSource(), filter));

		BufferedImage canvas = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = (Graphics2D)canvas.getGraphics();
		g.drawImage(transparentImage, 0, 0, null);
		g.dispose();

		return canvas;
	}
	
	@Override
	public void run()
	{
		int spkGraphicIndices[] = new int[0];
		
		try(FileInputStream spkiFis = new FileInputStream(m_sourceSpkiFile))
		{
			spkGraphicIndices = new SpkiIndexExtractor().getGraphicIndice(spkiFis);
		} catch (IOException | SpkiParseException e)
		{
			m_logger.error("Unable to extract spki indices, assuming no indexes.", e);
		}
		
		if(spkGraphicIndices.length == 0)
			m_logger.info("No spk graphic indices associated to respective SPK document. There is nothing to extract.");
		else
		{
			try(RandomAccessFile spk = new RandomAccessFile(m_sourceSpkFile, "r"))
			{
				SpkGraphicExtractor spkExtractor = new SpkGraphicExtractor();
				
				for(int i : spkGraphicIndices)
				{
					spk.getChannel().position(i);
					BufferedImage imgBuffer = filterWhiteToTransparent(spkExtractor.decompressImage(Channels.newInputStream(spk.getChannel()), m_spkCopyFillPattern));
					
					m_logger.info(String.format("Extracted image at origin %d, of width %d and height %d", i, imgBuffer.getWidth(), imgBuffer.getHeight()));
				
					try
					{
						ImageIO.write(imgBuffer, "png", new File(m_destinationDirectory.resolve(String.format("./texture_%d_0x%08X.png", i, i))));
					} catch (IOException e)
					{
						
						m_logger.error("Error occured extracting image to output directory", e);
					}
				}
			} catch (IOException | SpkGraphicParseException e)
			{
				m_logger.error("Error encountered extracting graphic images. Terminating extraction operation.", e);
			}
		}
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
			
			boolean copyFillPatternBuffer[] = new boolean[copyFillPattern.length()];
			
			for(int i = 0; i < copyFillPattern.length(); i++)
				copyFillPatternBuffer[i] = copyFillPattern.charAt(i) == '1';
			
			File f = new File(args[2]);
			if(f.exists() && f.isDirectory())
				new SpkDumper(args[0], args[1], f.toURI(), copyFillPatternBuffer).run();
			else
				m_logger.error("Destination argument must be a directory that already exists. Operation aborted. The provided destination argument was not valid.");
		}
	}
}
