// This file is part of MicropolisJ.
// Copyright (C) 2013 Jason Long
// Portions Copyright (C) 1989-2007 Electronic Arts Inc.
//
// MicropolisJ is free software; you can redistribute it and/or modify
// it under the terms of the GNU GPLv3, with additional terms.
// See the README file, included in this distribution, for details.

package micropolisj.engine;

import java.io.*;
import java.util.*;
import static micropolisj.engine.TileConstants.*;

/**
 * Subway network model: connected components of the subway under-layer,
 * the subway stations sitting on them, per-station ridership, and the
 * animated trains that shuttle between stations.
 *
 * Connectivity rules:
 * <ul>
 * <li>Plain subway cells are NOT road connections.</li>
 * <li>A trip may only enter the subway at a station, and exits at another
 *     station of the same connected network (component).</li>
 * <li>A network with fewer than two stations carries no trips and no trains.</li>
 * </ul>
 * The model is rebuilt lazily (BFS) whenever the subway layer or a station
 * tile changes. All access happens on the Swing event thread (the simulation
 * timer and painting both run there).
 */
public class SubwayNetwork
{
	/** Train progress units per tile. */
	public static final int STEPS_PER_TILE = 16;
	/** Train progress per animation tick (1/4 tile). */
	static final int TRAIN_SPEED = 4;
	/** Dwell time at a station, in animation ticks (~1s at Normal speed). */
	static final int DWELL_TICKS = 8;
	/** Hard cap on trains per network. */
	static final int MAX_TRAINS_PER_NETWORK = 10;

	public static class Station
	{
		public final int x;
		public final int y;
		int component = -1;
		/** Trips boarding or alighting here in the current month. */
		int ridersThisMonth;
		/** Trips boarding or alighting here last month. */
		int ridersLastMonth;
		/** BFS distance (in subway cells) to this station, or null. */
		int [] dist;

		Station(int x, int y)
		{
			this.x = x;
			this.y = y;
		}

		public int getComponent() { return component; }
		public int getRidersThisMonth() { return ridersThisMonth; }
		public int getRidersLastMonth() { return ridersLastMonth; }

		boolean contains(int cx, int cy)
		{
			return cx >= x && cx < x + 2 && cy >= y && cy < y + 2;
		}
	}

	public static class Train
	{
		int component;
		int curX, curY;
		int nextX = -1, nextY = -1;
		int prevX = -1, prevY = -1;
		int progress;
		int dwell;
		int dirX = 1, dirY = 0;
		Station lastStation;
		Station target;

		/** Position in 1/16 tile units (tile centre = +8). */
		public int getX16()
		{
			int b = curX * STEPS_PER_TILE + STEPS_PER_TILE / 2;
			return nextX < 0 ? b : b + (nextX - curX) * progress;
		}

		public int getY16()
		{
			int b = curY * STEPS_PER_TILE + STEPS_PER_TILE / 2;
			return nextY < 0 ? b : b + (nextY - curY) * progress;
		}

		/** True if heading east/west (for drawing orientation). */
		public boolean isHorizontal()
		{
			return dirX != 0;
		}

		public boolean isDwelling()
		{
			return dwell > 0;
		}
	}

	/** Receives notification after trains moved (for repainting). */
	public interface TrainListener
	{
		void subwayTrainsMoved();
	}

	final Micropolis city;
	boolean dirty = true;
	int width, height;
	/** Component id per cell (-1 = no subway). */
	int [] comp = new int[0];
	/** Station index per cell (-1 = none). */
	int [] stationAt = new int[0];
	int componentCount;
	int [] stationsPerComp = new int[0];
	final ArrayList<Station> stations = new ArrayList<Station>();
	/** Ridership etc. survives rebuilds; keyed by y*width+x of the key tile. */
	final HashMap<Integer,Station> stationsByKey = new HashMap<Integer,Station>();
	final ArrayList<Train> trains = new ArrayList<Train>();
	final Random rnd = new Random();
	final ArrayList<TrainListener> listeners = new ArrayList<TrainListener>();
	int lastNotifiedCount;

	SubwayNetwork(Micropolis city)
	{
		this.city = city;
	}

	public void addTrainListener(TrainListener l)
	{
		listeners.add(l);
	}

	public void removeTrainListener(TrainListener l)
	{
		listeners.remove(l);
	}

	void markDirty()
	{
		dirty = true;
	}

	void ensure()
	{
		if (dirty || width != city.getWidth() || height != city.getHeight()) {
			rebuild();
		}
	}

	int idx(int x, int y)
	{
		return y * width + x;
	}

	void rebuild()
	{
		dirty = false;
		width = city.getWidth();
		height = city.getHeight();
		int n = width * height;
		if (comp.length != n) {
			comp = new int[n];
			stationAt = new int[n];
		}
		Arrays.fill(comp, -1);
		Arrays.fill(stationAt, -1);

		// 1. Connected components of the subway layer (4-neighbour BFS)
		int [] queue = new int[n];
		componentCount = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int i = idx(x, y);
				if (comp[i] != -1 || !city.hasSubway(x, y)) {
					continue;
				}
				int c = componentCount++;
				int head = 0, tail = 0;
				queue[tail++] = i;
				comp[i] = c;
				while (head < tail) {
					int cur = queue[head++];
					int cx = cur % width, cy = cur / width;
					for (int d = 0; d < 4; d++) {
						int nx = cx + DX[d], ny = cy + DY[d];
						if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
						int ni = idx(nx, ny);
						if (comp[ni] == -1 && city.hasSubway(nx, ny)) {
							comp[ni] = c;
							queue[tail++] = ni;
						}
					}
				}
			}
		}

		// 2. Stations (key tile = top-left of the 2x2)
		HashMap<Integer,Station> old = new HashMap<Integer,Station>(stationsByKey);
		stations.clear();
		stationsByKey.clear();
		stationsPerComp = new int[componentCount];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if (city.getTile(x, y) != SUBWAYSTATION) {
					continue;
				}
				int key = idx(x, y);
				Station s = old.get(key);
				if (s == null) {
					s = new Station(x, y);
				}
				s.component = -1;
				s.dist = null;
				for (int dy = 0; dy < 2; dy++) {
					for (int dx = 0; dx < 2; dx++) {
						int sx = x + dx, sy = y + dy;
						if (sx >= width || sy >= height) continue;
						stationAt[idx(sx, sy)] = stations.size();
						if (s.component == -1 && comp[idx(sx, sy)] != -1) {
							s.component = comp[idx(sx, sy)];
						}
					}
				}
				if (s.component != -1) {
					stationsPerComp[s.component]++;
				}
				stations.add(s);
				stationsByKey.put(key, s);
			}
		}

		// 3. Distance maps for stations on networks with 2+ stations
		for (Station s : stations) {
			if (s.component == -1 || stationsPerComp[s.component] < 2) {
				continue;
			}
			int [] dist = new int[n];
			Arrays.fill(dist, -1);
			int head = 0, tail = 0;
			for (int dy = 0; dy < 2; dy++) {
				for (int dx = 0; dx < 2; dx++) {
					int sx = s.x + dx, sy = s.y + dy;
					if (sx >= width || sy >= height) continue;
					int si = idx(sx, sy);
					if (comp[si] == s.component) {
						dist[si] = 0;
						queue[tail++] = si;
					}
				}
			}
			while (head < tail) {
				int cur = queue[head++];
				int cx = cur % width, cy = cur / width;
				for (int d = 0; d < 4; d++) {
					int nx = cx + DX[d], ny = cy + DY[d];
					if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
					int ni = idx(nx, ny);
					if (dist[ni] == -1 && comp[ni] == s.component) {
						dist[ni] = dist[cur] + 1;
						queue[tail++] = ni;
					}
				}
			}
			s.dist = dist;
		}

		syncTrains();
	}

	static final int [] DX = { 0, 1, 0, -1 };
	static final int [] DY = { -1, 0, 1, 0 };

	// ---------------------------------------------------------------
	// Queries used by the simulation / UI
	// ---------------------------------------------------------------

	/** Station occupying the given cell, or null. */
	public Station getStationAt(int x, int y)
	{
		ensure();
		if (x < 0 || y < 0 || x >= width || y >= height) return null;
		int si = stationAt[idx(x, y)];
		return si >= 0 ? stations.get(si) : null;
	}

	/** Connected subway network id of a cell (-1 = none). */
	public int getComponent(int x, int y)
	{
		ensure();
		if (x < 0 || y < 0 || x >= width || y >= height) return -1;
		return comp[idx(x, y)];
	}

	/** Number of stations on the given network. */
	public int getStationCount(int component)
	{
		ensure();
		return component >= 0 && component < stationsPerComp.length ? stationsPerComp[component] : 0;
	}

	public int getTotalStationCount()
	{
		ensure();
		return stations.size();
	}

	/** Pick a random other station on the same network (null if none). */
	Station pickDestination(Station from, Random prng)
	{
		ensure();
		if (from == null || from.component == -1 || stationsPerComp[from.component] < 2) {
			return null;
		}
		int k = prng.nextInt(stationsPerComp[from.component] - 1);
		for (Station s : stations) {
			if (s == from || s.component != from.component) continue;
			if (k-- == 0) return s;
		}
		return null;
	}

	void recordTrip(Station from, Station to)
	{
		if (from != null) from.ridersThisMonth++;
		if (to != null) to.ridersThisMonth++;
	}

	/** Called once per game month: roll ridership counters. */
	void monthlyUpdate()
	{
		ensure();
		for (Station s : stations) {
			s.ridersLastMonth = s.ridersThisMonth;
			s.ridersThisMonth = 0;
		}
		syncTrains();
	}

	// ---------------------------------------------------------------
	// Trains
	// ---------------------------------------------------------------

	public List<Train> getTrains()
	{
		return Collections.unmodifiableList(trains);
	}

	int desiredTrains(int component)
	{
		int ns = stationsPerComp[component];
		if (ns < 2) return 0;
		int riders = 0;
		for (Station s : stations) {
			if (s.component == component) {
				riders += Math.max(s.ridersLastMonth, s.ridersThisMonth);
			}
		}
		int want = (ns - 1) + riders / 100;
		want = Math.min(want, Math.min(ns * 2, MAX_TRAINS_PER_NETWORK));
		return Math.max(1, want);
	}

	void syncTrains()
	{
		// Drop trains whose network vanished or whose track was removed
		for (Iterator<Train> it = trains.iterator(); it.hasNext(); ) {
			Train t = it.next();
			boolean ok = t.curX < width && t.curY < height
				&& comp[idx(t.curX, t.curY)] != -1;
			if (ok) {
				t.component = comp[idx(t.curX, t.curY)];
				ok = stationsPerComp[t.component] >= 2;
			}
			if (ok && t.nextX >= 0 && comp[idx(t.nextX, t.nextY)] != t.component) {
				t.nextX = t.nextY = -1;
				t.progress = 0;
			}
			if (ok && t.target != null && !stations.contains(t.target)) {
				t.target = null;
			}
			if (ok && t.lastStation != null && !stations.contains(t.lastStation)) {
				t.lastStation = null;
			}
			if (!ok) {
				it.remove();
			}
		}

		int [] have = new int[componentCount];
		for (Train t : trains) {
			have[t.component]++;
		}
		for (int c = 0; c < componentCount; c++) {
			int want = desiredTrains(c);
			// remove extras
			for (int i = trains.size() - 1; i >= 0 && have[c] > want; i--) {
				if (trains.get(i).component == c) {
					trains.remove(i);
					have[c]--;
				}
			}
			// add missing, spawning at the network's stations in turn
			if (have[c] < want) {
				ArrayList<Station> ss = new ArrayList<Station>();
				for (Station s : stations) {
					if (s.component == c) ss.add(s);
				}
				int k = have[c];
				while (have[c] < want && !ss.isEmpty()) {
					Station s = ss.get(k++ % ss.size());
					Train t = spawnAt(s, c);
					if (t == null) break;
					trains.add(t);
					have[c]++;
				}
			}
		}
	}

	Train spawnAt(Station s, int c)
	{
		for (int dy = 0; dy < 2; dy++) {
			for (int dx = 0; dx < 2; dx++) {
				int sx = s.x + dx, sy = s.y + dy;
				if (sx < width && sy < height && comp[idx(sx, sy)] == c) {
					Train t = new Train();
					t.component = c;
					t.curX = sx;
					t.curY = sy;
					t.lastStation = s;
					t.dwell = DWELL_TICKS / 2 + rnd.nextInt(DWELL_TICKS);
					return t;
				}
			}
		}
		return null;
	}

	/** Advance trains one animation tick. Called from Micropolis.animate(). */
	void animate()
	{
		ensure();
		boolean moved = trains.size() != lastNotifiedCount;
		lastNotifiedCount = trains.size();
		if (trains.isEmpty() && !moved) {
			return;
		}
		for (Train t : trains) {
			moved |= step(t);
		}
		if (moved) {
			for (TrainListener l : listeners) {
				l.subwayTrainsMoved();
			}
		}
	}

	/** @return true if the train's drawn position may have changed. */
	boolean step(Train t)
	{
		if (t.dwell > 0) {
			t.dwell--;
			return false;
		}
		if (t.nextX < 0) {
			if (!chooseNext(t)) {
				t.dwell = DWELL_TICKS; // nowhere to go right now; wait
				return false;
			}
		}
		t.progress += TRAIN_SPEED;
		if (t.progress >= STEPS_PER_TILE) {
			t.prevX = t.curX;
			t.prevY = t.curY;
			t.curX = t.nextX;
			t.curY = t.nextY;
			t.nextX = t.nextY = -1;
			t.progress = 0;

			Station s = stationAt[idx(t.curX, t.curY)] >= 0 ? stations.get(stationAt[idx(t.curX, t.curY)]) : null;
			if (s != null && s != t.lastStation && s.component == t.component) {
				// arrived at a station: stop and pick a new destination
				t.lastStation = s;
				t.target = null;
				t.dwell = DWELL_TICKS;
			}
			else if (s == null && t.lastStation != null && t.target == null) {
				// left the station area without a plan (should be rare)
				t.lastStation = null;
			}
		}
		return true;
	}

	boolean chooseNext(Train t)
	{
		if (t.target == null || t.target == t.lastStation || t.target.dist == null
			|| t.target.component != t.component)
		{
			t.target = pickTarget(t);
			if (t.target == null) return false;
		}
		int [] dist = t.target.dist;
		int here = dist[idx(t.curX, t.curY)];
		if (here <= 0) {
			// already inside target footprint (or unreachable): retarget
			t.lastStation = t.target;
			t.target = pickTarget(t);
			if (t.target == null) return false;
			dist = t.target.dist;
			here = dist[idx(t.curX, t.curY)];
			if (here <= 0) return false;
		}
		int best = -1;
		for (int d = 0; d < 4; d++) {
			int nx = t.curX + DX[d], ny = t.curY + DY[d];
			if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
			int nd = dist[idx(nx, ny)];
			if (nd >= 0 && nd < here) {
				// prefer going straight to avoid zig-zagging
				if (best == -1 || (DX[d] == t.dirX && DY[d] == t.dirY)) {
					best = d;
				}
			}
		}
		if (best == -1) return false;
		t.nextX = t.curX + DX[best];
		t.nextY = t.curY + DY[best];
		t.dirX = DX[best];
		t.dirY = DY[best];
		return true;
	}

	/**
	 * Next destination: the nearest other station that lets the train keep
	 * going in its current direction; at a line end, reverse to the nearest.
	 */
	Station pickTarget(Train t)
	{
		int here = idx(t.curX, t.curY);
		Station bestFwd = null, bestAny = null;
		int dFwd = Integer.MAX_VALUE, dAny = Integer.MAX_VALUE;
		for (Station s : stations) {
			if (s == t.lastStation || s.component != t.component || s.dist == null) continue;
			int d = s.dist[here];
			if (d <= 0) continue;
			if (d < dAny) { dAny = d; bestAny = s; }
			if (!firstHopIsBack(t, s) && d < dFwd) { dFwd = d; bestFwd = s; }
		}
		return bestFwd != null ? bestFwd : bestAny;
	}

	boolean firstHopIsBack(Train t, Station s)
	{
		if (t.prevX < 0) return false;
		int here = s.dist[idx(t.curX, t.curY)];
		boolean viaOther = false, viaBack = false;
		for (int d = 0; d < 4; d++) {
			int nx = t.curX + DX[d], ny = t.curY + DY[d];
			if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
			int nd = s.dist[idx(nx, ny)];
			if (nd >= 0 && nd < here) {
				if (nx == t.prevX && ny == t.prevY) viaBack = true;
				else viaOther = true;
			}
		}
		return viaBack && !viaOther;
	}

	// ---------------------------------------------------------------
	// Save / load (optional 'SSTN' trailer after the 'SWAY' trailer)
	// ---------------------------------------------------------------

	static final int STATION_MAGIC = 0x5353544E; // 'SSTN'
	static final int STATION_VERSION = 1;

	void write(DataOutputStream out)
		throws IOException
	{
		ensure();
		out.writeInt(STATION_MAGIC);
		out.writeShort(STATION_VERSION);
		out.writeShort(stations.size());
		for (Station s : stations) {
			out.writeShort(s.x);
			out.writeShort(s.y);
			out.writeInt(s.ridersThisMonth);
			out.writeInt(s.ridersLastMonth);
		}
	}

	/** Clear all state (new city / load). */
	void reset()
	{
		stationsByKey.clear();
		stations.clear();
		trains.clear();
		dirty = true;
	}

	/** @return false if no trailer was present. */
	boolean load(DataInputStream dis)
		throws IOException
	{
		reset();
		if (dis.available() < 8) {
			return false;
		}
		if (dis.readInt() != STATION_MAGIC) {
			return false;
		}
		int ver = dis.readUnsignedShort();
		int count = dis.readUnsignedShort();
		if (ver != STATION_VERSION) {
			return true;
		}
		int w = city.getWidth();
		for (int i = 0; i < count; i++) {
			int x = dis.readUnsignedShort();
			int y = dis.readUnsignedShort();
			Station s = new Station(x, y);
			s.ridersThisMonth = dis.readInt();
			s.ridersLastMonth = dis.readInt();
			stationsByKey.put(y * w + x, s);
		}
		return true;
	}
}
