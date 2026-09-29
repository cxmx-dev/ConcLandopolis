// This file is part of MicropolisJ.
// Copyright (C) 2013 Jason Long
// Portions Copyright (C) 1989-2007 Electronic Arts Inc.
//
// MicropolisJ is free software; you can redistribute it and/or modify
// it under the terms of the GNU GPLv3, with additional terms.
// See the README file, included in this distribution, for details.

package micropolisj.engine;

public interface ToolEffectIfc
{
	/**
	 * Gets the tile at a relative location.
	 * @return a non-negative tile identifier
	 */
	int getTile(int dx, int dy);

	void makeSound(int dx, int dy, Sound sound);

	/**
	 * Sets the tile value at a relative location.
	 */
	void setTile(int dx, int dy, int tileValue);

	/**
	 * Gets the elevation (height-map value) at a relative location.
	 * @see Micropolis#getElevation
	 */
	int getElevation(int dx, int dy);

	/**
	 * Sets the elevation at a relative location.
	 * @see Micropolis#setElevation
	 */
	void setElevation(int dx, int dy, int elevation);

	/**
	 * Gets the subway under-layer mask at a relative location.
	 * 0 = none; 1-15 = connectivity bits (N=1,E=2,S=4,W=8).
	 * @see Micropolis#getSubway
	 */
	int getSubway(int dx, int dy);

	/**
	 * Sets the subway under-layer mask (0 clears).
	 * @see Micropolis#setSubway
	 */
	void setSubway(int dx, int dy, int subwayMask);

	/**
	 * Deduct an amount from the controller's cash funds.
	 */
	void spend(int amount);

	void toolResult(ToolResult tr);
}
