// This file is part of MicropolisJ.
// Copyright (C) 2013 Jason Long
// Portions Copyright (C) 1989-2007 Electronic Arts Inc.
//
// MicropolisJ is free software; you can redistribute it and/or modify
// it under the terms of the GNU GPLv3, with additional terms.
// See the README file, included in this distribution, for details.

package micropolisj.engine;

import static micropolisj.engine.TileConstants.*;

/**
 * Places subway track on the under-layer ({@link Micropolis#subwayMap}).
 * Works under any land tile (elevation &gt;= {@link Micropolis#HEIGHT_LAND}),
 * including under roads, zones, and buildings. Does not modify the surface tile.
 * Cost is {@link MicropolisTool#SUBWAY} per cell.
 */
class SubwayTool extends ToolStroke
{
	static final int SUBWAY_COST = 100;

	// Connectivity bits matching RoadTable / RailTable (N=1, E=2, S=4, W=8)
	static final int N = 1, E = 2, S = 4, W = 8;

	SubwayTool(Micropolis city, int xpos, int ypos)
	{
		super(city, MicropolisTool.SUBWAY, xpos, ypos);
	}

	@Override
	public CityRect getBounds()
	{
		assert tool.getWidth() == 1;
		assert tool.getHeight() == 1;

		if (Math.abs(xdest - xpos) >= Math.abs(ydest - ypos)) {
			CityRect r = new CityRect();
			r.x = Math.min(xpos, xdest);
			r.width = Math.abs(xdest - xpos) + 1;
			r.y = ypos;
			r.height = 1;
			return r;
		}
		else {
			CityRect r = new CityRect();
			r.x = xpos;
			r.width = 1;
			r.y = Math.min(ypos, ydest);
			r.height = Math.abs(ydest - ypos) + 1;
			return r;
		}
	}

	@Override
	protected void applyArea(ToolEffectIfc eff)
	{
		for (;;) {
			if (!applyForward(eff)) {
				break;
			}
			if (!applyBackward(eff)) {
				break;
			}
		}
	}

	boolean applyBackward(ToolEffectIfc eff)
	{
		boolean any = false;
		CityRect b = getBounds();
		for (int i = b.height - 1; i >= 0; i--) {
			for (int j = b.width - 1; j >= 0; j--) {
				any = applyOne(new TranslatedToolEffect(eff, b.x + j, b.y + i)) || any;
			}
		}
		return any;
	}

	boolean applyForward(ToolEffectIfc eff)
	{
		boolean any = false;
		CityRect b = getBounds();
		for (int i = 0; i < b.height; i++) {
			for (int j = 0; j < b.width; j++) {
				any = applyOne(new TranslatedToolEffect(eff, b.x + j, b.y + i)) || any;
			}
		}
		return any;
	}

	boolean applyOne(ToolEffectIfc eff)
	{
		int elev = eff.getElevation(0, 0);
		if (elev < Micropolis.HEIGHT_LAND) {
			// No subway under water
			return false;
		}

		int existing = eff.getSubway(0, 0);
		boolean already = existing != 0 && existing != CLEAR;
		if (!already) {
			// Stub mask; fixSubway sets proper connectivity
			eff.setSubway(0, 0, E | W);
			eff.spend(SUBWAY_COST);
		}

		fixSubway(eff);
		fixSubway(new TranslatedToolEffect(eff, 0, -1));
		fixSubway(new TranslatedToolEffect(eff, 1, 0));
		fixSubway(new TranslatedToolEffect(eff, 0, 1));
		fixSubway(new TranslatedToolEffect(eff, -1, 0));
		return !already;
	}

	/**
	 * Recompute subway connectivity mask from orthogonal subway neighbors.
	 */
	void fixSubway(ToolEffectIfc eff)
	{
		int cur = eff.getSubway(0, 0);
		if (cur == 0 || cur == CLEAR) {
			return; // no subway on this cell
		}

		int mask = 0;
		// Connect toward any neighboring subway cell
		if (hasSubwayValue(eff.getSubway(0, -1))) {
			mask |= N;
		}
		if (hasSubwayValue(eff.getSubway(1, 0))) {
			mask |= E;
		}
		if (hasSubwayValue(eff.getSubway(0, 1))) {
			mask |= S;
		}
		if (hasSubwayValue(eff.getSubway(-1, 0))) {
			mask |= W;
		}

		if (mask == 0) {
			// Isolated segment: orient along the stroke axis
			CityRect b = getBounds();
			if (b.width >= b.height) {
				mask = E | W;
			} else {
				mask = N | S;
			}
		}

		if (mask != cur) {
			eff.setSubway(0, 0, mask);
		}
	}

	/**
	 * Recompute one cell's subway mask from its orthogonal subway neighbors
	 * (no-op if the cell has no subway). Used by the subway station tool.
	 */
	static void fixSubwayCell(ToolEffectIfc eff, int isolatedMask)
	{
		int cur = eff.getSubway(0, 0);
		if (!hasSubwayValue(cur)) {
			return;
		}
		int mask = 0;
		if (hasSubwayValue(eff.getSubway(0, -1))) mask |= N;
		if (hasSubwayValue(eff.getSubway(1, 0))) mask |= E;
		if (hasSubwayValue(eff.getSubway(0, 1))) mask |= S;
		if (hasSubwayValue(eff.getSubway(-1, 0))) mask |= W;
		if (mask == 0) {
			mask = isolatedMask;
		}
		if (mask != cur) {
			eff.setSubway(0, 0, mask);
		}
	}

	static boolean hasSubwayValue(int sub)
	{
		return sub != 0 && sub != CLEAR;
	}
}
