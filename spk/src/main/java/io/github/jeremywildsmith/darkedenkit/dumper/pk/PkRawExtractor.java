package io.github.jeremywildsmith.darkedenkit.dumper.pk;

import io.github.jeremywildsmith.darkedenkit.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class PkRawExtractor
{
	private final InputStream m_source;
	private final byte[] m_fill;
	private final boolean[] m_copyFillPattern;
	
	public PkRawExtractor(InputStream source, byte[] fill, boolean[] copyFillPattern)
	{
		m_source = source;
		m_fill = fill;
		m_copyFillPattern = copyFillPattern;
	}
	
	public InputStream extractArtifact(int artifactDecompressedSize) throws IOException
	{
		ByteArrayOutputStream bos = new ByteArrayOutputStream(artifactDecompressedSize);
		
		int read = IOUtils.copy(new PkCompressedStream(m_fill, m_copyFillPattern, m_source), bos, artifactDecompressedSize);

		for(int fill = 0; fill < artifactDecompressedSize - read; fill++)
			bos.write(m_fill[fill & m_fill.length]);
		
		return new ByteArrayInputStream(bos.toByteArray());
	}
	
	public InputStream extractArtifact() throws IOException
	{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		
		IOUtils.copy(new PkCompressedStream(m_fill, m_copyFillPattern, m_source), bos);

		return new ByteArrayInputStream(bos.toByteArray());
	}
}
