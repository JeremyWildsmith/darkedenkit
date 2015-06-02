package io.github.jeremywildsmith.darkedenkit.dumper.pk;

import io.github.jeremywildsmith.darkedenkit.LittleEndianDataInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CfpkExtractor
{
	private ArtifactAnimationPerspective[] readArtifactAnimationPerspectives(LittleEndianDataInputStream dis) throws IOException
	{
		int numPerspectives = dis.readByte();
		
		if(numPerspectives < 0)
			throw new IOException("Negative perspective array length declared.");
		
		ArtifactAnimationPerspective perspectives[] = new ArtifactAnimationPerspective[numPerspectives];
		
		for(int x = 0; x < numPerspectives; x++)
		{
			int numFrames = dis.readUnsignedShort();
			
			if(numFrames < 0)
				throw new IOException("Negative frames array length declared.");
			
			List<ArtifactAnimationFrame> frames = new ArrayList<>();
			
			for(int y = 0; y < numFrames; y++)
			{
				ArtifactAnimationFrame frame = new ArtifactAnimationFrame(dis.readUnsignedShort(), dis.readShort(), dis.readShort());
				
				if(frames.size() > 0 && frames.get(frames.size() - 1).equals(frame))
					frames.get(frames.size() - 1).incrementDurationFactor();
				else
					frames.add(frame);
			}
			
			perspectives[x] = new ArtifactAnimationPerspective(frames.toArray(new ArtifactAnimationFrame[frames.size()]));
		}
		
		return perspectives;
	}
	
	private ArtifactAnimation[] readArtifactAnimations(LittleEndianDataInputStream dis) throws IOException
	{
		int numAnimations = dis.readUnsignedByte();
		
		if(numAnimations < 0)
			throw new IOException("Negative animations array length declared.");
		
		ArtifactAnimation artifactAnimations[] = new ArtifactAnimation[numAnimations];
		
		for(int x = 0; x < numAnimations; x++)
			artifactAnimations[x] = new ArtifactAnimation(readArtifactAnimationPerspectives(dis));
		
		return artifactAnimations;
	}
	
	private Artifact[] readArtifacts(LittleEndianDataInputStream dis) throws IOException
	{
		int numArtifacts = dis.readUnsignedShort();
		
		if(numArtifacts < 0)
			throw new IOException("Negative artifact array length declared.");
		
		Artifact artifacts[] = new Artifact[numArtifacts];
		//These for loops really should be broken into at least one or two methods...
		for(int i = 0; i < numArtifacts; i++)
			artifacts[i] = new Artifact(readArtifactAnimations(dis));
		
		return artifacts;
	}
	
	public Artifact[] extract(InputStream spkiSource) throws IOException
	{
		return readArtifacts(new LittleEndianDataInputStream(spkiSource));
	}
	
	public static final class ArtifactAnimationFrame
	{
		private final int m_spki;
		private final int m_originX;
		private final int m_originY;
		private int m_durationFactor;
		
		public ArtifactAnimationFrame(int spki, int originX, int originY)
		{
			m_spki = spki;
			m_originX = originX;
			m_originY = originY;
			m_durationFactor = 1;
		}
		
		public int getSpki()
		{
			return m_spki;
		}
		
		public int getOriginX()
		{
			return m_originX;
		}
		
		public int getOriginY()
		{
			return m_originY;
		}
		
		public int getDurationFactor()
		{
			return m_durationFactor;
		}
		
		private void incrementDurationFactor()
		{
			m_durationFactor++;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + m_originX;
			result = prime * result + m_originY;
			result = prime * result + m_spki;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ArtifactAnimationFrame other = (ArtifactAnimationFrame) obj;
			if (m_originX != other.m_originX)
				return false;
			if (m_originY != other.m_originY)
				return false;
			if (m_spki != other.m_spki)
				return false;
			return true;
		}
		
	}
	
	public static final class ArtifactAnimationPerspective
	{
		private final ArtifactAnimationFrame m_frames[];
		
		public ArtifactAnimationPerspective(ArtifactAnimationFrame[] frames)
		{
			m_frames = frames;
		}
		
		public ArtifactAnimationFrame[] getFrames()
		{
			return m_frames;
		}
	}
	
	public static final class ArtifactAnimation
	{
		private final ArtifactAnimationPerspective m_perspectives[];
		
		public ArtifactAnimation(ArtifactAnimationPerspective[] perspectives)
		{
			m_perspectives = perspectives;
		}
		
		public ArtifactAnimationPerspective[] getPerspectives()
		{
			return m_perspectives;
		}
		
		public Set<Integer> getDependentSpki()
		{
			Set<Integer> dependentSpki = new HashSet<>();
			for(ArtifactAnimationPerspective p : m_perspectives)
			{
				for(ArtifactAnimationFrame f : p.getFrames())
					dependentSpki.add(f.getSpki());
			}
			
			return dependentSpki;
		}
	}
	
	public static final class Artifact
	{
		private final ArtifactAnimation[] m_animations;
		
		public Artifact(ArtifactAnimation[] animations)
		{
			m_animations = animations;
		}
		
		public ArtifactAnimation[] getAnimations()
		{
			return m_animations;
		}
	}
}
