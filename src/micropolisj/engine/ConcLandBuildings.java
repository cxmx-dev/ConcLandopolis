// This file is part of ConcLandopolis (a MicropolisJ derivative).
// MicropolisJ is free software under the GNU GPLv3, with additional terms.
// Building roster, costs and ideas ported from ConcLand
// (https://github.com/kurogedelic/ConcLand, MIT License, (c) 2026 Leo Kuroshita).

package micropolisj.engine;

import java.util.*;

/**
 * Table of the ConcLand-derived buildings: extra power plants, civic
 * buildings, Japanese specials and farms. Each building is a normal
 * MicropolisJ zone building (tiles 1024+ in tiles.rc, behavior=CONCLAND);
 * this class holds the gameplay numbers used by MapScanner.doConcLand().
 */
public final class ConcLandBuildings
{
	public static final class Spec
	{
		public final MicropolisTool tool;
		/** key ("zone center") tile */
		public final char key;
		/** power units supplied (coal=700, nuclear=2000) */
		public final int power;
		/** land-value bonus fed into the civic map (negative = nuisance) */
		public final int civic;
		/** crime reduction added to the police map */
		public final int police;
		/** commercial / industrial population added each scan */
		public final int comPop;
		public final int indPop;
		public final int description;
		/** needs grid power to give its civic/police/pop effect */
		public final boolean needsPower;
		/** spawns helicopters */
		public final boolean copter;

		Spec(MicropolisTool tool, char key, int power, int civic, int police,
			int comPop, int indPop, int description, boolean needsPower, boolean copter)
		{
			this.tool = tool;
			this.key = key;
			this.power = power;
			this.civic = civic;
			this.police = police;
			this.comPop = comPop;
			this.indPop = indPop;
			this.description = description;
			this.needsPower = needsPower;
			this.copter = copter;
		}
	}

	static final Spec [] ALL = {
		new Spec(MicropolisTool.CL_GAS, (char)1029, 1000, 0, 0, 0, 0, 33, true, false),
		new Spec(MicropolisTool.CL_OIL, (char)1045, 900, 0, 0, 0, 0, 34, true, false),
		new Spec(MicropolisTool.CL_SOLAR, (char)1056, 150, 0, 0, 0, 0, 35, true, false),
		new Spec(MicropolisTool.CL_WIND, (char)1060, 100, 0, 0, 0, 0, 36, true, false),
		new Spec(MicropolisTool.CL_SCHOOL, (char)1068, 0, 100, 0, 0, 0, 37, true, false),
		new Spec(MicropolisTool.CL_HOSPITAL, (char)1077, 0, 100, 0, 0, 0, 38, true, false),
		new Spec(MicropolisTool.CL_LIBRARY, (char)1086, 0, 80, 0, 0, 0, 39, true, false),
		new Spec(MicropolisTool.CL_UNIVERSITY, (char)1095, 0, 160, 0, 0, 0, 40, true, false),
		new Spec(MicropolisTool.CL_LAB, (char)1105, 0, 120, 0, 0, 1, 41, true, false),
		new Spec(MicropolisTool.CL_SPACE, (char)1121, 0, 250, 0, 2, 2, 42, true, false),
		new Spec(MicropolisTool.CL_PRISON, (char)1136, 0, -80, 800, 0, 0, 43, true, false),
		new Spec(MicropolisTool.CL_SHRINE, (char)1141, 0, 30, 0, 0, 0, 44, false, false),
		new Spec(MicropolisTool.CL_ONSEN, (char)1146, 0, 140, 0, 2, 0, 45, true, false),
		new Spec(MicropolisTool.CL_PACHINKO, (char)1155, 0, -40, 0, 4, 0, 46, true, false),
		new Spec(MicropolisTool.CL_HELIPORT, (char)1164, 0, 0, 0, 0, 0, 47, true, true),
		new Spec(MicropolisTool.CL_FARM, (char)1173, 0, 0, 0, 0, 2, 48, false, false),
	};

	private static final Map<Integer,Spec> byKey = new HashMap<Integer,Spec>();
	private static final Map<MicropolisTool,Spec> byTool = new EnumMap<MicropolisTool,Spec>(MicropolisTool.class);
	static {
		for (Spec s : ALL) {
			byKey.put((int) s.key, s);
			byTool.put(s.tool, s);
		}
	}

	private ConcLandBuildings() {}

	public static Spec forKeyTile(int tile)
	{
		return byKey.get(tile);
	}

	public static Spec forTool(MicropolisTool tool)
	{
		return byTool.get(tool);
	}

	public static boolean isConcLandTool(MicropolisTool tool)
	{
		return byTool.containsKey(tool);
	}

	/** True for the key tile of a ConcLand power plant (self-powered). */
	public static boolean isPowerPlant(int tile)
	{
		Spec s = byKey.get(tile);
		return s != null && s.power > 0;
	}

	public static Spec [] all()
	{
		return ALL.clone();
	}
}
