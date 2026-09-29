// This file is part of MicropolisJ.
// Copyright (C) 2013 Jason Long
// Portions Copyright (C) 1989-2007 Electronic Arts Inc.
//
// MicropolisJ is free software; you can redistribute it and/or modify
// it under the terms of the GNU GPLv3, with additional terms.
// See the README file, included in this distribution, for details.

package micropolisj.gui;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.Timer;

import micropolisj.engine.*;
import static micropolisj.engine.TileConstants.*;
import static micropolisj.gui.ColorParser.parseColor;

public class MicropolisDrawingArea extends JComponent
	implements Scrollable, MapListener, SubwayNetwork.TrainListener
{
	Micropolis m;
	boolean blinkUnpoweredZones = true;
	HashSet<Point> unpoweredZones = new HashSet<Point>();
	boolean blink;
	Timer blinkTimer;
	ToolCursor toolCursor;
	ToolPreview toolPreview;
	int shakeStep;

	static final Dimension PREFERRED_VIEWPORT_SIZE = new Dimension(800,700);
	static final ResourceBundle strings = MainWindow.strings;

	public static final int DEFAULT_TILE_SIZE = 16;
	public static final int MIN_TILE_SIZE = 8;
	public static final int MAX_TILE_SIZE = 32;
	TileImages tileImages;
	int TILE_WIDTH;
	int TILE_HEIGHT;
	int dragX, dragY;
	boolean dragging;

	/** Offscreen map of resolved tiles (no blink/preview/sprites). */
	BufferedImage tileBuffer;
	boolean tileBufferInvalid = true;
	/** Tiles waiting to be painted into tileBuffer before the next blit. */
	final HashSet<Point> pendingBufferTiles = new HashSet<Point>();

	public MicropolisDrawingArea(Micropolis engine)
	{
		this.m = engine;
		selectTileSize(DEFAULT_TILE_SIZE);
		m.addMapListener(this);
		m.getSubwayNetwork().addTrainListener(this);
		setDoubleBuffered(true);
		setOpaque(true);

		addAncestorListener(new AncestorListener() {
		public void ancestorAdded(AncestorEvent evt) {
			startBlinkTimer();
		}
		public void ancestorRemoved(AncestorEvent evt) {
			stopBlinkTimer();
		}
		public void ancestorMoved(AncestorEvent evt) {}
		});
		
		addMouseListener(new MouseListener() {
			
			@Override
			public void mousePressed(MouseEvent e) {
				if(e.getButton()==MouseEvent.BUTTON2)
					startDrag(e.getX(), e.getY());
			}
			
			@Override
			public void mouseReleased(MouseEvent e) {
				if(e.getButton()==MouseEvent.BUTTON2)
					endDrag(e.getX(), e.getY());
			}
			
			@Override
			public void mouseEntered(MouseEvent e) {
			}
			
			@Override
			public void mouseExited(MouseEvent e) {
			}
			
			@Override
			public void mouseClicked(MouseEvent e) {
			}
		});
		
		addMouseMotionListener(new MouseMotionListener() {
			
			@Override
			public void mouseMoved(MouseEvent e) {
			}
			
			@Override
			public void mouseDragged(MouseEvent e) {
				if(dragging)
					continueDrag(e.getX(), e.getY());
			}
		});
	}

	public void selectTileSize(int newTileSize)
	{
		tileImages = TileImages.getInstance(newTileSize);
		TILE_WIDTH = tileImages.TILE_WIDTH;
		TILE_HEIGHT = tileImages.TILE_HEIGHT;
		invalidateTileBuffer();
		revalidate();
		repaint();
	}

	public int getTileSize()
	{
		return TILE_WIDTH;
	}

	public CityLocation getCityLocation(int x, int y)
	{
		return new CityLocation(x / TILE_WIDTH, y / TILE_HEIGHT);
	}

	@Override
	public Dimension getPreferredSize()
	{
		assert this.m != null;

		return new Dimension(TILE_WIDTH*m.getWidth(),TILE_HEIGHT*m.getHeight());
	}

	public void setEngine(Micropolis newEngine)
	{
		assert newEngine != null;

		if (this.m != null) { //old engine
			this.m.removeMapListener(this);
			this.m.getSubwayNetwork().removeTrainListener(this);
		}
		this.m = newEngine;
		trainRects.clear();
		if (this.m != null) { //new engine
			this.m.addMapListener(this);
			this.m.getSubwayNetwork().addTrainListener(this);
		}

		// size may have changed
		invalidateTileBuffer();
		invalidate();
		repaint();
	}

	void invalidateTileBuffer()
	{
		tileBufferInvalid = true;
		pendingBufferTiles.clear();
	}

	void ensureTileBuffer()
	{
		final int width = m.getWidth();
		final int height = m.getHeight();
		final int pixW = width * TILE_WIDTH;
		final int pixH = height * TILE_HEIGHT;

		if (tileBuffer == null
			|| tileBuffer.getWidth() != pixW
			|| tileBuffer.getHeight() != pixH)
		{
			GraphicsConfiguration gc = getGraphicsConfiguration();
			if (gc != null) {
				tileBuffer = gc.createCompatibleImage(pixW, pixH, Transparency.OPAQUE);
			} else {
				tileBuffer = new BufferedImage(pixW, pixH, BufferedImage.TYPE_INT_RGB);
			}
			tileBufferInvalid = true;
		}

		if (!tileBufferInvalid) {
			return;
		}

		Graphics2D gr = tileBuffer.createGraphics();
		try {
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					gr.drawImage(tileImages.getTileImage(m.getTile(x, y)),
						x * TILE_WIDTH, y * TILE_HEIGHT, null);
				}
			}
		} finally {
			gr.dispose();
		}
		pendingBufferTiles.clear();
		tileBufferInvalid = false;
	}

	void flushPendingTilesToBuffer()
	{
		if (tileBufferInvalid) {
			ensureTileBuffer();
			return;
		}
		if (pendingBufferTiles.isEmpty() || tileBuffer == null) {
			return;
		}

		Graphics2D gr = tileBuffer.createGraphics();
		try {
			for (Point p : pendingBufferTiles) {
				if (p.x < 0 || p.y < 0 || p.x >= m.getWidth() || p.y >= m.getHeight()) {
					continue;
				}
				gr.drawImage(tileImages.getTileImage(m.getTile(p.x, p.y)),
					p.x * TILE_WIDTH, p.y * TILE_HEIGHT, null);
			}
		} finally {
			gr.dispose();
		}
		pendingBufferTiles.clear();
	}

	void drawSprite(Graphics gr, Sprite sprite)
	{
		assert sprite.isVisible();

		Point p = new Point(
			(sprite.x + sprite.offx) * TILE_WIDTH / 16,
			(sprite.y + sprite.offy) * TILE_HEIGHT / 16
			);

		Image img = tileImages.getSpriteImage(sprite.kind, sprite.frame-1);
		if (img != null) {
			gr.drawImage(img, p.x, p.y, null);
		}
		else {
			gr.setColor(Color.RED);
			gr.fillRect(p.x, p.y, 16, 16);
			gr.setColor(Color.WHITE);
			gr.drawString(Integer.toString(sprite.frame-1),p.x,p.y);
		}
	}

	public void paintComponent(Graphics gr)
	{
		final int width = m.getWidth();
		final int height = m.getHeight();

		Rectangle clipRect = gr.getClipBounds();
		if (clipRect == null) {
			clipRect = new Rectangle(0, 0, width * TILE_WIDTH, height * TILE_HEIGHT);
		}

		int minX = Math.max(0, clipRect.x / TILE_WIDTH);
		int minY = Math.max(0, clipRect.y / TILE_HEIGHT);
		int maxX = Math.min(width, 1 + (clipRect.x + clipRect.width - 1) / TILE_WIDTH);
		int maxY = Math.min(height, 1 + (clipRect.y + clipRect.height - 1) / TILE_HEIGHT);

		flushPendingTilesToBuffer();
		ensureTileBuffer();

		// Blit cached tiles (with optional earthquake shake per row)
		if (shakeStep != 0) {
			for (int y = minY; y < maxY; y++) {
				int dy = y * TILE_HEIGHT;
				int sx = getShakeModifier(y);
				int srcY = dy;
				int srcH = TILE_HEIGHT;
				gr.drawImage(tileBuffer,
					sx, dy, sx + width * TILE_WIDTH, dy + srcH,
					0, srcY, width * TILE_WIDTH, srcY + srcH,
					null);
			}
		} else {
			gr.drawImage(tileBuffer, 0, 0, null);
		}

		// Tool preview overlays (not baked into the buffer)
		if (toolPreview != null) {
			for (int y = minY; y < maxY; y++) {
				for (int x = minX; x < maxX; x++) {
					int c = toolPreview.getTile(x, y);
					if (c != CLEAR) {
						int dx = x * TILE_WIDTH + (shakeStep != 0 ? getShakeModifier(y) : 0);
						gr.drawImage(tileImages.getTileImage(c), dx, y * TILE_HEIGHT, null);
					}
				}
			}
		}

		// Elevation cliff/slope/shadow overlays from the height map.
		// Drawn after base tiles and tool-preview tiles so raise/lower previews show.
		for (int y = minY; y < maxY; y++) {
			for (int x = minX; x < maxX; x++) {
				int dx = x * TILE_WIDTH + (shakeStep != 0 ? getShakeModifier(y) : 0);
				drawElevationOverlay(gr, x, y, dx, y * TILE_HEIGHT);
			}
		}

		// Subway under-layer overlay (purple track lines)
		for (int y = minY; y < maxY; y++) {
			for (int x = minX; x < maxX; x++) {
				int dx = x * TILE_WIDTH + (shakeStep != 0 ? getShakeModifier(y) : 0);
				drawSubwayOverlay(gr, x, y, dx, y * TILE_HEIGHT);
			}
		}

		// Animated subway trains on the purple overlay
		drawSubwayTrains(gr, clipRect);

		// Unpowered-zone blink overlay; discover zones while scanning the clip
		if (blinkUnpoweredZones) {
			for (int y = minY; y < maxY; y++) {
				for (int x = minX; x < maxX; x++) {
					int cell = m.getTile(x, y);
					if (isZoneCenter(cell) && !m.isTilePowered(x, y) && !isSubwayStation(cell) && !isPowerFreeConcLand(cell)) {
						unpoweredZones.add(new Point(x, y));
						if (blink) {
							int dx = x * TILE_WIDTH + (shakeStep != 0 ? getShakeModifier(y) : 0);
							gr.drawImage(tileImages.getTileImage(LIGHTNINGBOLT),
								dx, y * TILE_HEIGHT, null);
						}
					}
				}
			}
		}

		Rectangle spriteClip = clipRect;
		for (Sprite sprite : m.allSprites())
		{
			if (sprite.isVisible())
			{
				Rectangle sb = getSpriteBounds(sprite, sprite.x, sprite.y);
				if (spriteClip.intersects(sb)) {
					drawSprite(gr, sprite);
				}
			}
		}

		if (toolCursor != null)
		{
			int x0 = toolCursor.rect.x * TILE_WIDTH;
			int x1 = (toolCursor.rect.x + toolCursor.rect.width) * TILE_WIDTH;
			int y0 = toolCursor.rect.y * TILE_HEIGHT;
			int y1 = (toolCursor.rect.y + toolCursor.rect.height) * TILE_HEIGHT;

			gr.setColor(Color.BLACK);
			gr.fillRect(x0-1, y0-1, x1-(x0-1), 1);
			gr.fillRect(x0-1, y0, 1, y1-y0);
			gr.fillRect(x0-3, y1+3, x1+4-(x0-3), 1);
			gr.fillRect(x1+3, y0-3, 1, y1+3-(y0-3));

			gr.setColor(Color.WHITE);
			gr.fillRect(x0-4, y0-4, x1+4-(x0-4), 1);
			gr.fillRect(x0-4, y0-3, 1, (y1+4)-(y0-3));
			gr.fillRect(x0-1, y1, x1+1-(x0-1), 1);
			gr.fillRect(x1, y0-1, 1, y1-(y0-1));

			gr.setColor(toolCursor.borderColor);
			gr.fillRect(x0-3, y0-3, x1+1-(x0-3), 2);
			gr.fillRect(x1+1, y0-3, 2, y1+1-(y0-3));
			gr.fillRect(x0-1, y1+1, x1+3-(x0-1), 2);
			gr.fillRect(x0-3, y0-1, 2, y1+3-(y0-1));

			if (toolCursor.fillColor != null) {
				gr.setColor(toolCursor.fillColor);
				gr.fillRect(x0,y0,x1-x0,y1-y0);
			}
		}
	}

	static class ToolCursor
	{
		CityRect rect;
		Color borderColor;
		Color fillColor;
	}

	public void setToolCursor(CityRect newRect, MicropolisTool tool)
	{
		ToolCursor tp = new ToolCursor();
		tp.rect = newRect;
		tp.borderColor = parseColor(
			strings.containsKey("tool."+tool.name()+".border") ?
			strings.getString("tool."+tool.name()+".border") :
			strings.getString("tool.*.border")
			);
		tp.fillColor = parseColor(
			strings.containsKey("tool."+tool.name()+".bgcolor") ?
			strings.getString("tool."+tool.name()+".bgcolor") :
			strings.getString("tool.*.bgcolor")
			);
		setToolCursor(tp);
	}

	public void setToolCursor(ToolCursor newCursor)
	{
		if (toolCursor == newCursor)
			return;
		if (toolCursor != null && toolCursor.equals(newCursor))
			return;

		if (toolCursor != null)
		{
			repaint(new Rectangle(
				toolCursor.rect.x*TILE_WIDTH - 4,
				toolCursor.rect.y*TILE_HEIGHT - 4,
				toolCursor.rect.width*TILE_WIDTH + 8,
				toolCursor.rect.height*TILE_HEIGHT + 8
				));
		}
		toolCursor = newCursor;
		if (toolCursor != null)
		{
			repaint(new Rectangle(
				toolCursor.rect.x*TILE_WIDTH - 4,
				toolCursor.rect.y*TILE_HEIGHT - 4,
				toolCursor.rect.width*TILE_WIDTH + 8,
				toolCursor.rect.height*TILE_HEIGHT + 8
				));
		}
	}

	public void setToolPreview(ToolPreview newPreview)
	{
		if (toolPreview != null) {
			repaint(elevationPreviewRepaintRect(toolPreview.getBounds()));
		}

		toolPreview = newPreview;
		if (toolPreview != null) {
			repaint(elevationPreviewRepaintRect(toolPreview.getBounds()));
		}
	}

	/** Inflate by one tile so neighbor cliff/shadow overlays update during preview. */
	private Rectangle elevationPreviewRepaintRect(CityRect b)
	{
		int x0 = Math.max(0, b.x - 1);
		int y0 = Math.max(0, b.y - 1);
		int x1 = Math.min(m.getWidth(), b.x + b.width + 1);
		int y1 = Math.min(m.getHeight(), b.y + b.height + 1);
		return new Rectangle(
			x0 * TILE_WIDTH,
			y0 * TILE_HEIGHT,
			(x1 - x0) * TILE_WIDTH,
			(y1 - y0) * TILE_HEIGHT
			);
	}

	//implements Scrollable
	public Dimension getPreferredScrollableViewportSize()
	{
		return PREFERRED_VIEWPORT_SIZE;
	}

	//implements Scrollable
	public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
	{
		if (orientation == SwingConstants.VERTICAL)
			return visibleRect.height;
		else
			return visibleRect.width;
	}

	//implements Scrollable
	public boolean getScrollableTracksViewportWidth()
	{
		return false;
	}

	//implements Scrollable
	public boolean getScrollableTracksViewportHeight()
	{
		return false;
	}

	//implements Scrollable
	public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
	{
		if (orientation == SwingConstants.VERTICAL)
			return TILE_HEIGHT * 3;
		else
			return TILE_WIDTH * 3;
	}

	private Rectangle getSpriteBounds(Sprite sprite, int x, int y)
	{
		return new Rectangle(
			(x+sprite.offx)*TILE_WIDTH/16,
			(y+sprite.offy)*TILE_HEIGHT/16,
			sprite.width*TILE_WIDTH/16,
			sprite.height*TILE_HEIGHT/16
			);
	}

	public Rectangle getTileBounds(int xpos, int ypos)
	{
		return new Rectangle(xpos*TILE_WIDTH, ypos * TILE_HEIGHT,
			TILE_WIDTH, TILE_HEIGHT);
	}

	//implements MapListener
	public void mapOverlayDataChanged(MapState overlayDataType)
	{
	}

	//implements MapListener
	public void spriteMoved(Sprite sprite)
	{
		repaint(getSpriteBounds(sprite, sprite.lastX, sprite.lastY));
		repaint(getSpriteBounds(sprite, sprite.x, sprite.y));
	}


	int effectiveElevation(int x, int y)
	{
		if (toolPreview != null) {
			int e = toolPreview.getElevation(x, y);
			if (e != CLEAR) {
				return e;
			}
		}
		if (x >= 0 && y >= 0 && x < m.getWidth() && y < m.getHeight()) {
			return m.getElevation(x, y);
		}
		return Micropolis.HEIGHT_LAND;
	}

	/**
	 * Draw cliff faces, slope shading, and soft shadows from neighbor height
	 * differences. Water stays flat at HEIGHT_WATER; raised inland plateaus
	 * become visible without changing the tile grid.
	 */
	void drawElevationOverlay(Graphics gr, int x, int y, int px, int py)
	{
		int h = effectiveElevation(x, y);
		int hN = effectiveElevation(x, y - 1);
		int hS = effectiveElevation(x, y + 1);
		int hW = effectiveElevation(x - 1, y);
		int hE = effectiveElevation(x + 1, y);

		Graphics2D g2 = (Graphics2D) gr;
		Composite old = g2.getComposite();

		// Plateau tint so raised flats read even without a cliff edge in view
		if (h > Micropolis.HEIGHT_LAND) {
			int alpha = Math.min(70, (h - Micropolis.HEIGHT_LAND) * 14);
			g2.setColor(new Color(60, 45, 25, alpha));
			g2.fillRect(px, py, TILE_WIDTH, TILE_HEIGHT);
		}

		// South-facing cliff band
		if (h > hS) {
			int dh = h - hS;
			int band = Math.max(2, Math.min(TILE_HEIGHT / 2, dh * Math.max(2, TILE_HEIGHT / 8)));
			drawCliffHorizontal(g2, px, py + TILE_HEIGHT - band, TILE_WIDTH, band, dh);
		}

		// East-facing cliff band
		if (h > hE) {
			int dh = h - hE;
			int band = Math.max(2, Math.min(TILE_WIDTH / 2, dh * Math.max(2, TILE_WIDTH / 8)));
			drawCliffVertical(g2, px + TILE_WIDTH - band, py, band, TILE_HEIGHT, dh);
		}

		// Soft shadow from higher neighbors to the north / west
		if (hN > h) {
			int dh = hN - h;
			int band = Math.max(1, Math.min(TILE_HEIGHT / 3, dh * Math.max(1, TILE_HEIGHT / 10)));
			g2.setColor(new Color(0, 0, 0, Math.min(110, 25 + dh * 22)));
			g2.fillRect(px, py, TILE_WIDTH, band);
		}
		if (hW > h) {
			int dh = hW - h;
			int band = Math.max(1, Math.min(TILE_WIDTH / 3, dh * Math.max(1, TILE_WIDTH / 10)));
			g2.setColor(new Color(0, 0, 0, Math.min(110, 25 + dh * 22)));
			g2.fillRect(px, py, band, TILE_HEIGHT);
		}

		// NW rim highlight on raised land
		if (h > Micropolis.HEIGHT_LAND && h >= hN && h >= hW) {
			g2.setColor(new Color(255, 255, 220, 55));
			g2.fillRect(px, py, Math.max(2, TILE_WIDTH / 5), Math.max(2, TILE_HEIGHT / 5));
		}

		g2.setComposite(old);
	}

	void drawCliffHorizontal(Graphics2D g2, int px, int py, int w, int band, int dh)
	{
		// Layered rock strata
		for (int i = 0; i < band; i++) {
			float t = (float) i / (float) Math.max(1, band - 1);
			int shade = (int) (70 + t * 50 + (dh > 2 ? 10 : 0));
			int r = Math.min(255, shade + 30);
			int g = Math.min(255, shade + 10);
			int b = Math.max(0, shade - 10);
			g2.setColor(new Color(r, g, b, 200));
			g2.fillRect(px, py + i, w, 1);
		}
		// Dark lip at top of cliff
		g2.setColor(new Color(40, 30, 20, 180));
		g2.fillRect(px, py, w, 1);
	}

	void drawCliffVertical(Graphics2D g2, int px, int py, int band, int hgt, int dh)
	{
		for (int i = 0; i < band; i++) {
			float t = (float) i / (float) Math.max(1, band - 1);
			int shade = (int) (55 + t * 45 + (dh > 2 ? 10 : 0));
			int r = Math.min(255, shade + 25);
			int g = Math.min(255, shade + 5);
			int b = Math.max(0, shade - 15);
			g2.setColor(new Color(r, g, b, 200));
			g2.fillRect(px + i, py, 1, hgt);
		}
		g2.setColor(new Color(40, 30, 20, 180));
		g2.fillRect(px, py, 1, hgt);
	}


	int effectiveSubway(int x, int y)
	{
		if (toolPreview != null) {
			int s = toolPreview.getSubway(x, y);
			if (s != CLEAR) {
				return s;
			}
		}
		if (x >= 0 && y >= 0 && x < m.getWidth() && y < m.getHeight()) {
			return m.getSubway(x, y);
		}
		return 0;
	}

	/** True if the (preview-aware) surface tile is part of a subway station. */
	boolean isStationCell(int x, int y)
	{
		if (x < 0 || y < 0 || x >= m.getWidth() || y >= m.getHeight()) {
			return false;
		}
		int c = CLEAR;
		if (toolPreview != null) {
			c = toolPreview.getTile(x, y);
		}
		if (c == CLEAR) {
			c = m.getTile(x, y);
		}
		return isSubwayStation(c);
	}

	/** ConcLand buildings that need no power (shrine, farm) never blink. */
	static boolean isPowerFreeConcLand(int cell)
	{
		ConcLandBuildings.Spec s = ConcLandBuildings.forKeyTile(cell & LOMASK);
		return s != null && !s.needsPower;
	}

	/**
	 * Draw subway under-layer as translucent purple track segments.
	 * Mask bits: N=1, E=2, S=4, W=8.
	 */
	void drawSubwayOverlay(Graphics gr, int x, int y, int px, int py)
	{
		int mask = effectiveSubway(x, y);
		if (mask == 0) {
			return;
		}

		// Under a subway station keep the building art readable: only draw
		// the stubs that lead out of the station to the rest of the line.
		boolean underStation = isStationCell(x, y);
		if (underStation) {
			if (isStationCell(x, y - 1)) mask &= ~1;
			if (isStationCell(x + 1, y)) mask &= ~2;
			if (isStationCell(x, y + 1)) mask &= ~4;
			if (isStationCell(x - 1, y)) mask &= ~8;
			if (mask == 0) {
				return;
			}
		}

		Graphics2D g2 = (Graphics2D) gr;
		Composite old = g2.getComposite();
		int cx = px + TILE_WIDTH / 2;
		int cy = py + TILE_HEIGHT / 2;
		int t = Math.max(2, TILE_WIDTH / 6);

		g2.setColor(new Color(160, 60, 200, 200));
		Stroke oldStroke = g2.getStroke();
		g2.setStroke(new BasicStroke(Math.max(2f, TILE_WIDTH / 8f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

		// Always draw a small hub (except inside a station)
		if (!underStation) {
			g2.fillOval(cx - t / 2, cy - t / 2, t, t);
		}

		if ((mask & 1) != 0) { // N
			g2.drawLine(cx, cy, cx, py);
		}
		if ((mask & 2) != 0) { // E
			g2.drawLine(cx, cy, px + TILE_WIDTH, cy);
		}
		if ((mask & 4) != 0) { // S
			g2.drawLine(cx, cy, cx, py + TILE_HEIGHT);
		}
		if ((mask & 8) != 0) { // W
			g2.drawLine(cx, cy, px, cy);
		}

		// Glow outline
		g2.setColor(new Color(220, 140, 255, 90));
		g2.setStroke(new BasicStroke(Math.max(3f, TILE_WIDTH / 5f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		if ((mask & 1) != 0) g2.drawLine(cx, cy, cx, py);
		if ((mask & 2) != 0) g2.drawLine(cx, cy, px + TILE_WIDTH, cy);
		if ((mask & 4) != 0) g2.drawLine(cx, cy, cx, py + TILE_HEIGHT);
		if ((mask & 8) != 0) g2.drawLine(cx, cy, px, cy);

		g2.setStroke(oldStroke);
		g2.setComposite(old);
	}

	// ---------------------------------------------------------------
	// Subway trains
	// ---------------------------------------------------------------

	/** Screen rectangles where trains were last drawn (for dirty repaint). */
	final ArrayList<Rectangle> trainRects = new ArrayList<Rectangle>();

	static final Color TRAIN_OUTLINE = new Color(20, 10, 30);
	static final Color TRAIN_BODY = new Color(255, 225, 40);
	static final Color TRAIN_NOSE = Color.WHITE;

	/** Bounds of a train including its dark outline. */
	Rectangle trainBounds(SubwayNetwork.Train t)
	{
		int cx = t.getX16() * TILE_WIDTH / SubwayNetwork.STEPS_PER_TILE;
		int cy = t.getY16() * TILE_HEIGHT / SubwayNetwork.STEPS_PER_TILE;
		// Body: ~2/3 tile long, ~1/5 tile thick (3x2 px at 50%, 10x5 at 200%)
		int len = Math.max(3, TILE_WIDTH * 5 / 8);
		int thk = Math.max(2, TILE_WIDTH / 5);
		int w = t.isHorizontal() ? len : thk;
		int h = t.isHorizontal() ? thk : len;
		return new Rectangle(cx - w / 2 - 1, cy - h / 2 - 1, w + 2, h + 2);
	}

	void drawSubwayTrains(Graphics gr, Rectangle clip)
	{
		java.util.List<SubwayNetwork.Train> trains = m.getSubwayNetwork().getTrains();
		if (trains.isEmpty()) {
			return;
		}
		for (SubwayNetwork.Train t : trains) {
			Rectangle r = trainBounds(t);
			if (clip != null && !clip.intersects(r)) {
				continue;
			}
			int sx = shakeStep != 0 ? getShakeModifier(r.y / TILE_HEIGHT) : 0;
			int x = r.x + sx, y = r.y;
			// dark outline
			gr.setColor(TRAIN_OUTLINE);
			gr.fillRect(x, y, r.width, r.height);
			// bright body
			gr.setColor(TRAIN_BODY);
			gr.fillRect(x + 1, y + 1, r.width - 2, r.height - 2);
			// white cab/headlight at both ends (bidirectional stock);
			// the whole body glows white while dwelling at a station
			gr.setColor(TRAIN_NOSE);
			if (t.isDwelling()) {
				int inset = Math.max(1, Math.min(r.width, r.height) / 4);
				gr.fillRect(x + inset, y + inset,
					Math.max(1, r.width - 2 * inset), Math.max(1, r.height - 2 * inset));
			}
			else if (t.isHorizontal()) {
				gr.fillRect(x + 1, y + 1, 1, r.height - 2);
				gr.fillRect(x + r.width - 2, y + 1, 1, r.height - 2);
			}
			else {
				gr.fillRect(x + 1, y + 1, r.width - 2, 1);
				gr.fillRect(x + 1, y + r.height - 2, r.width - 2, 1);
			}
		}
	}

	/** Dirty areas accumulated since the last flush (old + new train spots). */
	final ArrayList<Rectangle> pendingTrainDirty = new ArrayList<Rectangle>();
	boolean trainFlushScheduled;

	//implements SubwayNetwork.TrainListener
	public void subwayTrainsMoved()
	{
		// Collect "where it was" + "where it is" per train; several sim
		// ticks per timer event are coalesced into a single flush below.
		java.util.List<SubwayNetwork.Train> trains = m.getSubwayNetwork().getTrains();
		ArrayList<Rectangle> now = new ArrayList<Rectangle>(trains.size());
		for (SubwayNetwork.Train t : trains) {
			now.add(trainBounds(t));
		}
		for (int i = 0; i < trainRects.size(); i++) {
			Rectangle r = new Rectangle(trainRects.get(i));
			if (i < now.size() && r.intersects(grow(now.get(i), TILE_WIDTH))) {
				r.add(now.get(i));
			}
			pendingTrainDirty.add(r);
		}
		for (int i = 0; i < now.size(); i++) {
			pendingTrainDirty.add(now.get(i));
		}
		trainRects.clear();
		trainRects.addAll(now);

		if (!trainFlushScheduled) {
			trainFlushScheduled = true;
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					flushTrainDirty();
				}
			});
		}
	}

	static Rectangle grow(Rectangle r, int d)
	{
		return new Rectangle(r.x - d, r.y - d, r.width + 2 * d, r.height + 2 * d);
	}

	void flushTrainDirty()
	{
		trainFlushScheduled = false;
		if (pendingTrainDirty.isEmpty()) {
			return;
		}
		// Merge overlapping rects, then paint each small region directly so
		// distant trains do not get unioned into one huge repaint area.
		ArrayList<Rectangle> merged = new ArrayList<Rectangle>();
		for (Rectangle r : pendingTrainDirty) {
			Rectangle g = new Rectangle(r.x - 5, r.y, r.width + 10, r.height); // quake shake slack
			boolean done = false;
			for (Rectangle mr : merged) {
				if (mr.intersects(g)) {
					mr.add(g);
					done = true;
					break;
				}
			}
			if (!done) {
				merged.add(g);
			}
		}
		pendingTrainDirty.clear();
		if (!isShowing() || merged.size() > 64) {
			for (Rectangle r : merged) {
				repaint(r);
			}
			return;
		}
		for (Rectangle r : merged) {
			paintImmediately(r);
		}
	}

	//implements MapListener
	public void tileChanged(int xpos, int ypos)
	{
		pendingBufferTiles.add(new Point(xpos, ypos));
		repaint(getTileBounds(xpos, ypos));
	}

	//implements MapListener
	public void wholeMapChanged()
	{
		invalidateTileBuffer();
		repaint();
	}

	protected void startDrag(int x, int y)
	{
		dragging = true;
		dragX = x;
		dragY = y;
	}
	protected void endDrag(int x, int y)
	{
		dragging = false;
	}
	protected void continueDrag(int x, int y)
	{
		int dx = x - dragX;		
		int dy = y - dragY;
		JScrollPane js = (JScrollPane)getParent().getParent();
		js.getHorizontalScrollBar().setValue(
				js.getHorizontalScrollBar().getValue()-dx);
		js.getVerticalScrollBar().setValue(
				js.getVerticalScrollBar().getValue()-dy);
	}
	
	void doBlink()
	{
		if (!unpoweredZones.isEmpty())
		{
			blink = !blink;
			for (Point loc : unpoweredZones)
			{
				repaint(getTileBounds(loc.x, loc.y));
			}
			unpoweredZones.clear();
		}
	}

	void startBlinkTimer()
	{
		assert blinkTimer == null;

		ActionListener callback = new ActionListener() {
		public void actionPerformed(ActionEvent evt)
		{
			doBlink();
		}
		};

		blinkTimer = new Timer(500, callback);
		blinkTimer.setCoalesce(true);
		blinkTimer.start();
	}

	void stopBlinkTimer()
	{
		if (blinkTimer != null) {
			blinkTimer.stop();
			blinkTimer = null;
		}
	}

	void shake(int i)
	{
		shakeStep = i;
		repaint();
	}

	static final int SHAKE_STEPS = 40;
	int getShakeModifier(int row)
	{
		return (int)Math.round(4.0 * Math.sin((double)(shakeStep+row/2)/2.0));
	}
}
