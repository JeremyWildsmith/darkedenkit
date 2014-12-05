package io.github.jeremywildsmith.darkedenkit.spk;

import io.github.jeremywildsmith.darkedenkit.LittleEndianDataInputStream;
import io.github.jeremywildsmith.darkedenkit.LittleEndianDataOutputStream;

import java.awt.Image;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

public class SpkGraphicExtractor
{
	private static final int BITMAPFILEINFOHEADER_SIZE = 14;
	private static final int BITMAPINFOHEADER_SIZE = 40;
	private static final int BITMAPCOMPRESSION_BITFIELDS = 3;
	
	//Since the streams are provided by the caller, it is their responsibility to close them.
	@SuppressWarnings("resource")
	private void decompressChunkStream(InputStream is, OutputStream os) throws SpkGraphicParseException
	{
		LittleEndianDataInputStream dis = new LittleEndianDataInputStream(is);
		LittleEndianDataOutputStream dos = new LittleEndianDataOutputStream(os);
		
		boolean isCopyNotFill = false;

		try
		{
			while(dis.available() != 0)
			{
				short wordC = dis.readShort();
					
				if(isCopyNotFill)
				{
					for(int x = 0; x < wordC; x++)
						dos.writeShort(dis.readShort());
				} else
				{
					for(int x = 0; x < wordC; x++)
						dos.writeShort(0xFFFF);
				}
				
				isCopyNotFill = !isCopyNotFill;
			}
		} catch (IOException e)
		{
			throw new SpkGraphicParseException("IO error occured attempting to decompress SPK chunk.", e);
		}
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
		dos.writeInt(0x07E0);
		dos.writeInt(0x001F);
		
	}
	
	//Since the caller provided the streams, it is their responsibility to close them.
	@SuppressWarnings("resource")
	public void decompressImage(InputStream compressedImageIn, OutputStream bitmapOut) throws SpkGraphicParseException
	{
		LittleEndianDataInputStream dis = new LittleEndianDataInputStream(compressedImageIn);
		LittleEndianDataOutputStream dos = new LittleEndianDataOutputStream(bitmapOut);
		
		int imageWidth = 0;
		int imageHeight = 0;
		
		try
		{
			imageWidth = dis.readShort();
			imageHeight = dis.readShort();
		} catch (IOException e)
		{
			throw new SpkGraphicParseException("Error occured extracting spk graphic width/height", e);
		}
		
		int evenImageWidth = (imageWidth % 2) == 0 ? imageWidth : imageWidth + 1;
		
		List<byte[]> decompressedWidthChunks = new ArrayList<>();
		
		for(int y = 0; y < imageHeight; y++)
		{
			short sizeOfChunkInWords = 0;
			boolean isLineFilled = true;
			
			try
			{
				sizeOfChunkInWords = dis.readShort();
				isLineFilled = dis.readShort() != 0;
			} catch (IOException e)
			{
				throw new SpkGraphicParseException("Error occured reading SPK chunk reader", e);
			}
			
			if(isLineFilled)
			{
				ByteArrayOutputStream chunckDecompressionBuffer = new ByteArrayOutputStream(evenImageWidth * 2);
				byte chunkBuffer[] = new byte[sizeOfChunkInWords * 2 - 2]; //Chunk size includes the isLineFilled member. So subtract to, calculating the size of the chunk data.
				
				//Because I can.
				int totalRead = 0;
				try
				{
					for(int read = 0; 
						totalRead < chunkBuffer.length && (read = dis.read(chunkBuffer, totalRead, chunkBuffer.length - totalRead)) != -1; 
						totalRead += read);
				
				} catch (IOException e)
				{
					throw new SpkGraphicParseException("Error occured attempting to read compressed graphic chunck into memory.", e);
				}
				
				if(totalRead < chunkBuffer.length)
					throw new SpkGraphicParseException(String.format("Insufficient data to complete decompression of chunck, missing %d bytes.", chunkBuffer.length - totalRead));
				
				decompressChunkStream(new ByteArrayInputStream(chunkBuffer), chunckDecompressionBuffer);

				decompressedWidthChunks.add(chunckDecompressionBuffer.toByteArray());
			}else
			{
				byte blank[] = new byte[sizeOfChunkInWords];
				Arrays.fill(blank, (byte)-1);
				decompressedWidthChunks.add(blank);
			}
		}

		try
		{
			writeSpkBitmapHeader(dos, imageWidth, imageHeight);
		} catch (IOException e)
		{
			throw new SpkGraphicParseException("Error occured writing out spk image bitmap header.", e);
		}
		
		try
		{
			//Write bitmap data (pixel data)
			for(byte[] widthBitmapData : decompressedWidthChunks)
			{
				dos.write(widthBitmapData);	
				for(int i = widthBitmapData.length / 2; i < evenImageWidth; i++)
					dos.write(new byte[] {-1, -1});
			}
		} catch (IOException e)
		{
			throw new SpkGraphicParseException("Error occured writing out spk image bitmap pixel data.", e);
		}
	}
	
	public Image decompressImage(InputStream compressedImageIn) throws SpkGraphicParseException
	{
		try
		{
			ByteArrayOutputStream bitmapBuffer = new ByteArrayOutputStream();
			decompressImage(compressedImageIn, bitmapBuffer);

			return ImageIO.read(new ByteArrayInputStream(bitmapBuffer.toByteArray()));
		} catch (IOException e)
		{
			throw new SpkGraphicParseException("Error occured generating image from compressed SPK graphic.", e);
		}
	}
	
	public static final class SpkGraphicParseException extends Exception
	{
		private static final long serialVersionUID = 1L;
	
		private SpkGraphicParseException(String message, Exception cause)
		{
			super(message, cause);
		}
		
		private SpkGraphicParseException(String message)
		{
			super(message);
		}
	}
}
