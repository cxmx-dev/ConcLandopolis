// This file is part of MicropolisJ.
// Copyright (C) 2013 Jason Long
// Portions Copyright (C) 1989-2007 Electronic Arts Inc.
//
// MicropolisJ is free software; you can redistribute it and/or modify
// it under the terms of the GNU GPLv3, with additional terms.
// See the README file, included in this distribution, for details.

package micropolisj.engine;

import java.util.*;
import static micropolisj.engine.TileConstants.*;

public class ToolPreview implements ToolEffectIfc
{
	public int offsetX;
	public int offsetY;
	public short [][] tiles;
	/** Parallel to tiles; CLEAR means elevation unchanged. */
	public short [][] elevations;
	/** Parallel to tiles; CLEAR means subway unchanged; 0 clears subway. */
	public short [][] subways;
	public int cost;
	public ToolResult toolResult;
	public List<SoundInfo> sounds;

	public static class SoundInfo
	{
		public int x;
		public int y;
		public Sound sound;

		SoundInfo(int x, int y, Sound sound)
		{
			this.x = x;
			this.y = y;
			this.sound = sound;
		}
	}

	ToolPreview()
	{
		this.tiles = new short[0][0];
		this.elevations = new short[0][0];
		this.subways = new short[0][0];
		this.sounds = new ArrayList<SoundInfo>();
		this.toolResult = ToolResult.NONE;
	}

	//implements ToolEffectIfc
	public int getTile(int dx, int dy)
	{
		if (inRange(dx, dy)) {
			return tiles[offsetY+dy][offsetX+dx];
		}
		else {
			return CLEAR;
		}
	}

	public CityRect getBounds()
	{
		return new CityRect(
			-offsetX,
			-offsetY,
			getWidth(),
			getHeight()
			);
	}

	int getWidth()
	{
		return tiles.length != 0 ? tiles[0].length : 0;
	}

	int getHeight()
	{
		return tiles.length;
	}

	boolean inRange(int dx, int dy)
	{
		return offsetY+dy >= 0 &&
			offsetY+dy < getHeight() &&
			offsetX+dx >= 0 &&
			offsetX+dx < getWidth();
	}

	void expandTo(int dx, int dy)
	{
		if (tiles == null || tiles.length == 0) {
			tiles = new short[1][1];
			tiles[0][0] = CLEAR;
			elevations = new short[1][1];
			elevations[0][0] = CLEAR;
			subways = new short[1][1];
			subways[0][0] = CLEAR;
			offsetX = -dx;
			offsetY = -dy;
			return;
		}

		// expand each existing row as needed
		for (int i = 0; i < tiles.length; i++) {
			tiles[i] = expandRow(tiles[i], dx);
			elevations[i] = expandRow(elevations[i], dx);
			subways[i] = expandRow(subways[i], dx);
		}

		if (offsetX+dx < 0) {
			int addl = -(offsetX+dx);
			offsetX += addl;
		}

		int width = tiles[0].length;
		if (offsetY+dy >= tiles.length) {
			int newLen = offsetY+dy+1;
			tiles = expandRows(tiles, newLen, width, false);
			elevations = expandRows(elevations, newLen, width, false);
			subways = expandRows(subways, newLen, width, false);
		}
		else if (offsetY+dy < 0) {
			int addl = -(offsetY+dy);
			int newLen = tiles.length + addl;
			tiles = expandRows(tiles, newLen, width, true);
			elevations = expandRows(elevations, newLen, width, true);
			subways = expandRows(subways, newLen, width, true);
			offsetY += addl;
		}
	}

	private short[] expandRow(short[] A, int dx)
	{
		if (offsetX+dx >= A.length) {
			int newLen = offsetX+dx+1;
			short[] AA = new short[newLen];
			System.arraycopy(A, 0, AA, 0, A.length);
			Arrays.fill(AA, A.length, newLen, CLEAR);
			return AA;
		}
		else if (offsetX+dx < 0) {
			int addl = -(offsetX+dx);
			int newLen = A.length + addl;
			short[] AA = new short[newLen];
			System.arraycopy(A, 0, AA, addl, A.length);
			Arrays.fill(AA, 0, addl, CLEAR);
			return AA;
		}
		return A;
	}

	private short[][] expandRows(short[][] src, int newLen, int width, boolean prepend)
	{
		short[][] dest = new short[newLen][width];
		if (prepend) {
			int addl = newLen - src.length;
			System.arraycopy(src, 0, dest, addl, src.length);
			for (int i = 0; i < addl; i++) {
				Arrays.fill(dest[i], CLEAR);
			}
		} else {
			System.arraycopy(src, 0, dest, 0, src.length);
			for (int i = src.length; i < newLen; i++) {
				Arrays.fill(dest[i], CLEAR);
			}
		}
		return dest;
	}

	//implements ToolEffectIfc
	public void makeSound(int dx, int dy, Sound sound)
	{
		sounds.add(new SoundInfo(dx, dy, sound));
	}

	//implements ToolEffectIfc
	public void setTile(int dx, int dy, int tileValue)
	{
		expandTo(dx, dy);
		tiles[offsetY+dy][offsetX+dx] = (short)tileValue;
	}


	//implements ToolEffectIfc
	public int getElevation(int dx, int dy)
	{
		if (inRange(dx, dy)) {
			return elevations[offsetY+dy][offsetX+dx];
		}
		else {
			return CLEAR;
		}
	}

	//implements ToolEffectIfc
	public void setElevation(int dx, int dy, int elevation)
	{
		expandTo(dx, dy);
		elevations[offsetY+dy][offsetX+dx] = (short)elevation;
	}

	//implements ToolEffectIfc
	public int getSubway(int dx, int dy)
	{
		if (inRange(dx, dy)) {
			return subways[offsetY+dy][offsetX+dx];
		}
		else {
			return CLEAR;
		}
	}

	//implements ToolEffectIfc
	public void setSubway(int dx, int dy, int subwayMask)
	{
		expandTo(dx, dy);
		subways[offsetY+dy][offsetX+dx] = (short)subwayMask;
	}

	//implements ToolEffectIfc
	public void spend(int amount)
	{
		cost += amount;
	}

	//implements ToolEffectIfc
	public void toolResult(ToolResult tr)
	{
		this.toolResult = tr;
	}
}
