package io.github.jeremywildsmith.darkedenkit;

public class Rect2D
{
	public final int x;
	public final int y;
	public final int width;
	public final int height;
	
	public Rect2D(int _x, int _y, int _width, int _height)
	{
		x = _x;
		y = _y;
		width = _width;
		height = _height;
	}
	
	public Rect2D()
	{
		this(0, 0, 0, 0);
	}
}
