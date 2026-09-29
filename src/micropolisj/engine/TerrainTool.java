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
 * Map-editor style terrain raise/lower paint tools.
 * Raise: fill water to land, or increase inland elevation (height map).
 * Lower: decrease elevation, or carve flat land into water at sea level.
 * Refuses roads, rails, wires, zones, and buildings.
 * <p>
 * Road/rail tunnels through raised land and the subway under-layer key off
 * {@link Micropolis#heightMap} / {@link Micropolis#subwayMap}.
 */
class TerrainTool extends ToolStroke
{
	// Same river-edge table as MapGenerator.smoothRiver()
	static final char [] REdTab = new char[] {
		RIVEDGE + 8, RIVEDGE + 8, RIVEDGE + 12, RIVEDGE + 10,
		RIVEDGE + 0, RIVER,       RIVEDGE + 14, RIVEDGE + 12,
		RIVEDGE + 4, RIVEDGE + 6, RIVER,        RIVEDGE + 8,
		RIVEDGE + 2, RIVEDGE + 4, RIVEDGE + 0,  RIVER
		};

	static final int [] DX = new int[] { -1, 0, 1, 0 };
	static final int [] DY = new int[] { 0, 1, 0, -1 };

	TerrainTool(Micropolis city, MicropolisTool tool, int xpos, int ypos)
	{
		super(city, tool, xpos, ypos);
		assert tool == MicropolisTool.RAISE || tool == MicropolisTool.LOWER;
	}

	@Override
	protected void applyArea(ToolEffectIfc eff)
	{
		CityRect b = getBounds();

		for (int y = 0; y < b.height; y++) {
			for (int x = 0; x < b.width; x++) {
				applyOne(new TranslatedToolEffect(eff, b.x + x, b.y + y));
			}
		}

		// Re-fit shoreline tiles in and around the stroke so coasts look classic.
		int x0 = Math.max(0, b.x - 1);
		int y0 = Math.max(0, b.y - 1);
		int x1 = Math.min(city.getWidth() - 1, b.x + b.width);
		int y1 = Math.min(city.getHeight() - 1, b.y + b.height);

		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				smoothShore(new TranslatedToolEffect(eff, x, y));
			}
		}
	}

	boolean applyOne(ToolEffectIfc eff)
	{
		int tile = eff.getTile(0, 0) & LOMASK;

		if (tool == MicropolisTool.RAISE) {
			return applyRaise(eff, tile);
		}
		else {
			return applyLower(eff, tile);
		}
	}

	boolean applyRaise(ToolEffectIfc eff, int tile)
	{
		int elev = eff.getElevation(0, 0);

		// Fill water / shoreline -> buildable dirt at land elevation
		if (isWaterTerrain(tile) || elev == Micropolis.HEIGHT_WATER) {
			if (!isWaterTerrain(tile) && !isEmptyTerrain(tile)) {
				// Non-terrain sitting at water height (unusual) — refuse
				eff.toolResult(ToolResult.UH_OH);
				return false;
			}
			if (isWaterTerrain(tile)) {
				eff.setTile(0, 0, DIRT);
			}
			eff.setElevation(0, 0, Micropolis.HEIGHT_LAND);
			eff.spend(tool.getToolCost());
			return true;
		}

		// Inland raise: increase elevation on empty natural terrain
		if (isEmptyTerrain(tile)) {
			if (elev >= Micropolis.MAX_ELEVATION) {
				return false;
			}
			eff.setElevation(0, 0, elev + 1);
			eff.spend(tool.getToolCost());
			return true;
		}

		// Roads, zones, buildings, etc.
		eff.toolResult(ToolResult.UH_OH);
		return false;
	}

	boolean applyLower(ToolEffectIfc eff, int tile)
	{
		int elev = eff.getElevation(0, 0);

		// Already at/below sea level water: nothing to do
		if (isWaterTerrain(tile) || elev <= Micropolis.HEIGHT_WATER) {
			return false;
		}

		// Lower raised land one step; at land level carve into water
		if (isEmptyTerrain(tile)) {
			if (elev > Micropolis.HEIGHT_LAND) {
				eff.setElevation(0, 0, elev - 1);
				eff.spend(tool.getToolCost());
				return true;
			}
			// elev == HEIGHT_LAND: sink to water
			eff.setTile(0, 0, RIVER);
			eff.setElevation(0, 0, Micropolis.HEIGHT_WATER);
			eff.spend(tool.getToolCost());
			return true;
		}

		// Roads, zones, buildings, etc.
		eff.toolResult(ToolResult.UH_OH);
		return false;
	}

	/**
	 * Water and river-edge tiles (RIVER .. LASTRIVEDGE).
	 */
	static boolean isWaterTerrain(int tile)
	{
		return tile >= RIVER && tile <= LASTRIVEDGE;
	}

	/**
	 * Empty / natural terrain safe to carve: dirt, woods, park greenery, rubble.
	 */
	static boolean isEmptyTerrain(int tile)
	{
		if (tile == DIRT) {
			return true;
		}
		if (tile >= TREEBASE && tile <= WOODS5) {
			return true;
		}
		if (isRubble(tile)) {
			return true;
		}
		if (tile == FOUNTAIN) {
			return true;
		}
		return false;
	}

	/**
	 * Recompute a single shoreline tile from its wet neighbors,
	 * matching MapGenerator.smoothRiver() / REdTab.
	 */
	void smoothShore(ToolEffectIfc eff)
	{
		int tile = eff.getTile(0, 0) & LOMASK;
		if (!isWaterTerrain(tile)) {
			return;
		}

		// Preserve deep channel tiles when still fully surrounded by water
		if (tile == CHANNEL) {
			if (isWaterTerrain(eff.getTile(0, -1) & LOMASK) &&
				isWaterTerrain(eff.getTile(1, 0) & LOMASK) &&
				isWaterTerrain(eff.getTile(0, 1) & LOMASK) &&
				isWaterTerrain(eff.getTile(-1, 0) & LOMASK))
			{
				return;
			}
		}

		int bitindex = 0;
		for (int z = 0; z < 4; z++) {
			bitindex <<= 1;
			int n = eff.getTile(DX[z], DY[z]) & LOMASK;
			if (isWaterTerrain(n)) {
				bitindex |= 1;
			}
		}

		char temp = REdTab[bitindex & 15];
		if (temp != RIVER && !inPreview && city.PRNG.nextInt(2) != 0) {
			temp++;
		}
		if ((eff.getTile(0, 0) & LOMASK) != temp) {
			eff.setTile(0, 0, temp);
		}
		// Water tiles always sit at sea level
		if (eff.getElevation(0, 0) != Micropolis.HEIGHT_WATER) {
			eff.setElevation(0, 0, Micropolis.HEIGHT_WATER);
		}
	}
}
