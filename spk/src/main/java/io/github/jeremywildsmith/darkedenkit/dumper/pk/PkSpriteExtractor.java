package io.github.jeremywildsmith.darkedenkit.dumper.pk;

import io.github.jeremywildsmith.darkedenkit.IOUtils;
import io.github.jeremywildsmith.darkedenkit.LittleEndianDataInputStream;
import io.github.jeremywildsmith.darkedenkit.LittleEndianDataOutputStream;
import io.github.jeremywildsmith.darkedenkit.Nullable;
import io.github.jeremywildsmith.darkedenkit.ReadLimitInputStream;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
import java.awt.image.RGBImageFilter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

public final class PkSpriteExtractor
{
	private static final int BITMAPFILEINFOHEADER_SIZE = 14;
	private static final int BITMAPINFOHEADER_SIZE = 40;
	private static final int BITMAPCOMPRESSION_BITFIELDS = 3;
	
	private static final byte[] SPRITE_FILL = new byte[] {-1};
	
	private final boolean[] m_copyFillPattern;
	
	public PkSpriteExtractor(boolean[] copyFillPattern)
	{
		m_copyFillPattern = copyFillPattern;
	}
	
	private void writeSpkBitmapHeader(OutputStream os, int imageWidth, int imageHeight) throws IOException
	{
		//Since the stream is provided by the caller, it is the caller's responsibility to close it.
		@SuppressWarnings("resource")
		LittleEndianDataOutputStream dos = new LittleEndianDataOutputStream(os);
		
		int evenImageWidth = imageWidth % 2 == 0 ? imageWidth : imageWidth + 1;
		
		//BITMAPFILEHEADER
		//Bitmap header signature
		dos.writeByte('B');
		dos.writeByte('M');
		
		//Bitmap file size (DWORD)
		dos.writeInt(BITMAPFILEINFOHEADER_SIZE + BITMAPINFOHEADER_SIZE + imageHeight * (evenImageWidth * 2));
		
		//Two reserved words (must be zero)
		dos.writeShort(0);
		dos.writeShort(0);
		
		//Offset from origin of file where pixel data starts. Since our header size never changes, this is constant.
		dos.writeInt(70);
		
		//BITMAPINFOHEADER
		//Write size of header.
		dos.writeInt(BITMAPINFOHEADER_SIZE);
		
		//Image width
		dos.writeInt(imageWidth);
		
		//Since all SPK compressed images are stored top-down, we must negate the height
		dos.writeInt(-imageHeight);
		
		//All bitmaps always have one plane
		dos.writeShort(1);
		
		//All SPK images are 16 bit
		dos.writeShort(16);
		
		//All SPK images are decompressed into compressed BI_BITFIELD bitmaps
		dos.writeInt(BITMAPCOMPRESSION_BITFIELDS);
		
		//Size of image data, in bytes.
		dos.writeInt(evenImageWidth * imageHeight * 2);

		//pixels per meter is constant throughout all SPK graphics in both x & y.
		dos.writeInt(2834);
		dos.writeInt(2834);
		
		//ClrUsed, Field not relevant to BI_BITFIELDS compressed bitmaps.
		dos.writeInt(0);
				
		//ClrImportant, Field not relevant to BI_BITFIELDS compressed bitmaps.
		dos.writeInt(0);
		
		//Write color bitmasks. These are constant throughout all SPK graphics.
		dos.writeInt(0xF800);
		dos.writeInt(0x07E0);
		dos.writeInt(0x1F);
		dos.writeInt(0);
	}
	
	//Since the caller provided the streams, it is their responsibility to close them.
	@SuppressWarnings("resource")
	private boolean decompressImage(InputStream compressedImageIn, OutputStream bitmapOut) throws IOException
	{
		LittleEndianDataInputStream dis = new LittleEndianDataInputStream(compressedImageIn);
		LittleEndianDataOutputStream dos = new LittleEndianDataOutputStream(bitmapOut);
		
		int imageWidth = dis.readShort();
		int imageHeight = dis.readShort();
		
		if(imageWidth <= 0 || imageHeight <= 0)
			return false;
		
		int evenImageWidth = (imageWidth % 2) == 0 ? imageWidth : imageWidth + 1;
		
		List<byte[]> decompressedWidthChunks = new ArrayList<>();
		
		for(int y = 0; y < imageHeight; y++)
		{
			int sizeOfChunkInWords = dis.readShort() - 1; //Minus 1 for unknown short that starts every chunk
			dis.skipBytes(2); //unknown short;
			
			ByteArrayOutputStream chunckDecompressionBuffer = new ByteArrayOutputStream(evenImageWidth * 2);
		
			IOUtils.copy(new PkCompressedStream(SPRITE_FILL, m_copyFillPattern, new ReadLimitInputStream(dis, sizeOfChunkInWords * 2)), 
							chunckDecompressionBuffer);
		
			decompressedWidthChunks.add(chunckDecompressionBuffer.toByteArray());
		}

		writeSpkBitmapHeader(dos, imageWidth, imageHeight);
		
		for(byte[] widthBitmapData : decompressedWidthChunks)
		{
			dos.write(widthBitmapData);	
			for(int i = widthBitmapData.length; i < evenImageWidth * 2; i++)
				dos.write(new byte[] {-1});
		}
		
		return true;
	}
	
	@Nullable
	private Image decompressImage(InputStream compressedImageIn) throws IOException
	{
		try
		{
			ByteArrayOutputStream bitmapBuffer = new ByteArrayOutputStream();
			
			if(!decompressImage(compressedImageIn, bitmapBuffer))
				return null;
				
			return ImageIO.read(new ByteArrayInputStream(bitmapBuffer.toByteArray()));
		} catch (IOException e)
		{
			throw new IOException("Error occured generating image from compressed SPK graphic.", e);
		}
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
	
	private BufferedImage generateNullImage()
	{
		BufferedImage img = new BufferedImage(200, 60, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = (Graphics2D)img.getGraphics();
		g2d.setColor(Color.red);
		g2d.drawString("NULL IMAGE", 10, 10);
		g2d.dispose();
		return img;
	}
	
	@Nullable
	public BufferedImage extract(InputStream pkSource) throws IOException
	{
		Image extractedImage = decompressImage(pkSource);
		BufferedImage imgBuffer = extractedImage == null ? generateNullImage() : filterWhiteToTransparent(extractedImage);
		return imgBuffer;
	}
}
