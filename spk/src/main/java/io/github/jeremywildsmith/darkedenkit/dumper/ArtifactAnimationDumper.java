package io.github.jeremywildsmith.darkedenkit.dumper;

import io.github.jeremywildsmith.darkedenkit.Nullable;
import io.github.jeremywildsmith.darkedenkit.Rect2D;
import io.github.jeremywildsmith.darkedenkit.dumper.pk.CfpkExtractor;
import io.github.jeremywildsmith.darkedenkit.dumper.pk.CfpkExtractor.Artifact;
import io.github.jeremywildsmith.darkedenkit.dumper.pk.CfpkExtractor.ArtifactAnimation;
import io.github.jeremywildsmith.darkedenkit.dumper.pk.CfpkExtractor.ArtifactAnimationFrame;
import io.github.jeremywildsmith.darkedenkit.dumper.pk.CfpkExtractor.ArtifactAnimationPerspective;
import io.github.jeremywildsmith.darkedenkit.dumper.pk.PkIndexExtractor;
import io.github.jeremywildsmith.darkedenkit.dumper.pk.PkIndexExtractor.PkiParseException;
import io.github.jeremywildsmith.darkedenkit.dumper.pk.PkSpriteExtractor;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ArtifactAnimationDumper implements Runnable
{
	private static final int MILLISECONDS_PER_TIME_FACTOR = 25;
	
	private static final Logger m_logger = LoggerFactory.getLogger(ArtifactAnimationDumper.class);

	private final File m_sourcePkFile;
	private final File m_sourcePkiFile;
	private final File m_sourceCfpkFile;
	private final URI m_destinationDirectory;
	private final PkSpriteExtractor m_extractor;

	private ArtifactAnimationDumper(File sourcePkFile, File sourcePkiFile, File sourceCfpkFile, URI destinationDirectory, boolean[] spkCopyFillPattern)
	{
		m_sourcePkFile = sourcePkFile;
		m_sourcePkiFile = sourcePkiFile;
		m_sourceCfpkFile = sourceCfpkFile;
		m_destinationDirectory = destinationDirectory;

		m_extractor = new PkSpriteExtractor(spkCopyFillPattern);
	}
	
	private void compileJsonSpriteMetadata(SpkSpriteSheet spritesheet, ArtifactAnimation artifactAnimation, File destination) throws IOException
	{
		ArtifactAnimationPerspective perspectives[] = artifactAnimation.getPerspectives();
		
		Map<String, Object> objectMapping = new HashMap<>();
		List<Map<String, Object>> animations = new ArrayList<>();
		
		objectMapping.put("scale", 1.0);
		objectMapping.put("texture", "texture.png");
		objectMapping.put("defaultAnimation", "0");
		objectMapping.put("animations", animations);
		
		for(int i = 0; i < perspectives.length; i++)
		{
			Map<String, Object> animation = new HashMap<String, Object>();
			List<Map<String, Object>> frames = new ArrayList<>();
			
			animation.put("name", String.valueOf(i));
			animation.put("frames", frames);
			
			animations.add(animation);
			for(ArtifactAnimationFrame f : perspectives[i].getFrames())
			{
				Rect2D frameRegion = spritesheet.getRegion(f.getSpki());
				Map<String, Object> frame = new HashMap<>();
				Map<String, Object> anchor = new HashMap<String, Object>();
				Map<String, Object> region = new HashMap<String, Object>();
				
				anchor.put("x", f.getOriginX());
				anchor.put("y", f.getOriginY());
				
				region.put("x", frameRegion.x);
				region.put("y", frameRegion.y);
				region.put("width", frameRegion.width);
				region.put("height", frameRegion.height);
				
				frame.put("anchor", anchor);
				frame.put("region", region);
				frame.put("delay", f.getDurationFactor() * MILLISECONDS_PER_TIME_FACTOR);
				
				frames.add(frame);
			}
		}
		
		ObjectMapper mapper = new ObjectMapper();
		mapper.writerWithDefaultPrettyPrinter().writeValue(destination, objectMapping);
	}
	
	private long[] getSpkIndices()
	{
		long pkIndices[] = new long[0];
		
		try(FileInputStream spkiFis = new FileInputStream(m_sourcePkiFile))
		{
			pkIndices = new PkIndexExtractor().extract(spkiFis);
		} catch (IOException | PkiParseException e)
		{
			m_logger.error("Unable to extract spki indices, assuming no indexes.", e);
		}
		
		return pkIndices;
	}
	
	@Nullable
	private SpkSpriteSheet compileSpkSpritesheet(RandomAccessFile spk, long[] spki, Set<Integer> includedSpks) throws IOException
	{
		Map<Integer, Rect2D> spritesheetFrames = new HashMap<>();
		List<BufferedImage> frames = new ArrayList<>();
		
		int maxHeight = 0;
		int totalWidth = 0;
		
		for(int i : includedSpks)
		{
			spk.getChannel().position(spki[i]);
			BufferedImage frame = m_extractor.extract(Channels.newInputStream(spk.getChannel()));
			
			if(frame == null)
				spritesheetFrames.put(i, new Rect2D());
			else
			{
				spritesheetFrames.put(i, new Rect2D(totalWidth, 0, frame.getWidth(), frame.getHeight()));
				maxHeight = Math.max(maxHeight, frame.getHeight());
				totalWidth += frame.getWidth();

				frames.add(frame);
			}
		}
		
		if(frames.size() <= 0)
			return null;
		
		BufferedImage spritesheet = new BufferedImage(totalWidth, maxHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = (Graphics2D)spritesheet.getGraphics();
		
		int offsetX = 0;
		for(BufferedImage img : frames)
		{
			g.drawImage(img, offsetX, 0, null);
			offsetX += img.getWidth();
		}
		g.dispose();
		
		return new SpkSpriteSheet(spritesheet, spritesheetFrames);
	}

	private void exportArtifact(RandomAccessFile spk, Artifact artifact, long[] spki, URI destinationDirectory) throws IOException
	{
		ArtifactAnimation animations[] = artifact.getAnimations();
		
		for(int i = 0; i < animations.length; i++)
		{
			File animationDirectory = new File(destinationDirectory.resolve(String.format("./%d/", i)));
			
			SpkSpriteSheet spritesheet = compileSpkSpritesheet(spk, spki, animations[i].getDependentSpki());
	
			//If the spritesheet is null, then there were no renderable frames in the animation (ie, it was likely culled out of the build. Ignore the animation.
			if(spritesheet != null)
			{
				if(!animationDirectory.exists() && !animationDirectory.mkdirs())
					m_logger.error("Unable to create required directories to extract animation to. Not extracting animation.");
				else
				{
					try(FileOutputStream fos = new FileOutputStream(new File(animationDirectory.toURI().resolve("./texture.png"))))
					{
						ImageIO.write(spritesheet.getSpritesheet(), "png", fos);
					}
				
					compileJsonSpriteMetadata(spritesheet, animations[i], new File(animationDirectory.toURI().resolve("./animation.jsf")));
				}
			}
		}
	}
	
	@Override
	public final void run()
	{
		m_logger.info("Gathering SPK indices...");
		long pkIndices[] = getSpkIndices();
		
		if(pkIndices.length == 0)
			m_logger.info("No spk graphic indices associated to respective SPK document. There is nothing to extract.");
		else
		{
			try(RandomAccessFile spk = new RandomAccessFile(m_sourcePkFile, "r");
				FileInputStream cfpk = new FileInputStream(m_sourceCfpkFile))
			{
				m_logger.info("Gathering artifact meta-data...");
				Artifact[] artifacts = new CfpkExtractor().extract(cfpk);
				
				for(int i = 0; i < artifacts.length; i++)
				{
					exportArtifact(spk, artifacts[i], pkIndices, m_destinationDirectory.resolve(String.format("./artifact%d/", i)));
					m_logger.info(String.format("Processed %d of %d artifacts. %.2f%% completed.", i + 1, artifacts.length, (i + 1.0F) / artifacts.length * 100.0F));
				}
			} catch (IOException e)
			{
				m_logger.error("Error encountered extracting artifacts. Terminating extraction operation.", e);
			}
		}
		
		m_logger.info("Done");
	}
	
	private static final class SpkSpriteSheet
	{
		private BufferedImage m_spritesheet;
		private Map<Integer, Rect2D> m_spkiMapping;
		
		public SpkSpriteSheet(BufferedImage spritesheet, Map<Integer, Rect2D> spkiMapping)
		{
			m_spritesheet = spritesheet;
			m_spkiMapping = spkiMapping;
		}
		
		public Rect2D getRegion(int spki)
		{
			Rect2D mapping = m_spkiMapping.get(spki);
			
			return mapping == null ? new Rect2D() : mapping;
		}
		
		public BufferedImage getSpritesheet()
		{
			return m_spritesheet;
		}
	}
	
	public static void main(String[] args)
	{
		m_logger.info("Dark Eden Artifact Animation Extraction Utility. Written by Jeremy Wildsmith. This software is open-sourced under GPLV3 license. The git repository for this project is hosted at https://github.com/JeremyWildsmith/darkedenkit");
		m_logger.info("Command Line Arguments: [spk source] [spki source] [spk copy fill pattern = 01(spk) 011(ispk)] [cfpk source] [destination directory]");
		
		if(args.length < 5)
			m_logger.error("Insufficient arguments provided to run dump operation");
		else
		{
			String copyFillPattern = args[2];
			
			if(copyFillPattern.isEmpty())
				m_logger.error("Invalid copy fill pattern supplied.");
			else
			{
				boolean copyFillPatternBuffer[] = new boolean[copyFillPattern.length()];
				
				for(int i = 0; i < copyFillPattern.length(); i++)
					copyFillPatternBuffer[i] = copyFillPattern.charAt(i) == '1';
				
				File f = new File(args[4]);
				if((!f.exists() && f.mkdirs()) || f.isDirectory())
					new ArtifactAnimationDumper(new File(args[0]), new File(args[1]), new File(args[3]), f.toURI(), copyFillPatternBuffer).run();
				else
					m_logger.error("Destination argument must be a directory. Either the specified destionation is not of a directory, or this application failed to construct the directory. Operation aborted. The provided destination argument was not valid.");
			}
		}
	}
}
