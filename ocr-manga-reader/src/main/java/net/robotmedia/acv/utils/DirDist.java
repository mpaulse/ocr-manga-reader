package net.robotmedia.acv.utils;

public class DirDist
{
  public enum D8
  {
    Top,
    TopRight,
    Right,
    BottomRight,
    Bottom,
    BottomLeft,
    Left,
    TopLeft,
    Last,
  }

  public D8 dir;
  public int dist;

  public DirDist(D8 dir, int dist)
  {
    this.dir = dir;
    this.dist = dist;
  }
  
}
