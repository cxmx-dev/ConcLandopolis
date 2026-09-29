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
 * Enumerates the various tools that can be applied to the map by the user.
 * Call the tool's apply() method to actually use the tool on the map.
 */
public enum MicropolisTool
{
	BULLDOZER(1, 1),
	WIRE(1, 5),   //cost=25 for underwater
	ROADS(1, 10), //cost=50 for over water
	RAIL(1, 20),  //cost=100 for underwater
	RESIDENTIAL(3, 100),
	COMMERCIAL(3, 100),
	INDUSTRIAL(3, 100),
	FIRE(3, 500),
	POLICE(3, 500),
	STADIUM(4, 5000),
	PARK(1, 10),
	RAISE(1, 1),
	LOWER(1, 1),
	SEAPORT(4, 3000),
	POWERPLANT(4, 3000),
	NUCLEAR(4, 5000),
	AIRPORT(6, 10000),
	STRIPMALL(4, 2000),
	TUNNEL(1, 100),
	SUBWAY(1, 100),
	SUBWAYSTATION(2, 500),
	// ConcLand buildings (see ConcLandBuildings)
	CL_GAS(4, 4000),
	CL_OIL(4, 3500),
	CL_SOLAR(2, 1500),
	CL_WIND(2, 1000),
	CL_SCHOOL(3, 1000),
	CL_HOSPITAL(3, 1500),
	CL_LIBRARY(3, 800),
	CL_UNIVERSITY(3, 3000),
	CL_LAB(4, 4000),
	CL_SPACE(4, 15000),
	CL_PRISON(3, 3000),
	CL_SHRINE(1, 300),
	CL_ONSEN(3, 2500),
	CL_PACHINKO(3, 2000),
	CL_HELIPORT(3, 2500),
	CL_FARM(3, 300),
	QUERY(1, 0);

	int size;
	int cost;

	private MicropolisTool(int size, int cost)
	{
		this.size = size;
		this.cost = cost;
	}

	public int getWidth()
	{
		return size;
	}

	public int getHeight()
	{
		return getWidth();
	}

	public ToolStroke beginStroke(Micropolis engine, int xpos, int ypos)
	{
		switch (this) {
		case BULLDOZER:
			return new Bulldozer(engine, xpos, ypos);

		case WIRE:
		case ROADS:
		case RAIL:
		case TUNNEL:
			return new RoadLikeTool(engine, this, xpos, ypos);

		case SUBWAY:
			return new SubwayTool(engine, xpos, ypos);

		case FIRE:
		case POLICE:
		case STADIUM:
		case SEAPORT:
		case POWERPLANT:
		case NUCLEAR:
		case AIRPORT:
		case STRIPMALL:
		case SUBWAYSTATION:
			return new BuildingTool(engine, this, xpos, ypos);


		case RAISE:
		case LOWER:
			return new TerrainTool(engine, this, xpos, ypos);
		default:
			if (ConcLandBuildings.isConcLandTool(this)) {
				return new BuildingTool(engine, this, xpos, ypos);
			}
			return new ToolStroke(engine, this, xpos, ypos);
		}
	}

	public ToolResult apply(Micropolis engine, int xpos, int ypos)
	{
		return beginStroke(engine, xpos, ypos).apply();
	}

	/**
	 * This is the cost displayed in the GUI when the tool is selected.
	 * It does not necessarily reflect the cost charged when a tool is
	 * applied, as extra may be charged for clearing land or building
	 * over or through water.
	 */
	public int getToolCost()
	{
		return cost;
	}
}
