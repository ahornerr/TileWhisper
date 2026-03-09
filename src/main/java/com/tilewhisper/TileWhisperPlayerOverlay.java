package com.tilewhisper;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import java.awt.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class TileWhisperPlayerOverlay extends Overlay
{
	private static final Color SPEAKING_BG_COLOR = new Color(76, 175, 80, 220);
	private static final Color SPEAKING_BORDER_COLOR = Color.WHITE;
	private static final int ICON_SIZE = 18;
	private static final int ICON_OFFSET_Y = 5;
	private static final long TRANSMIT_TIMEOUT_MS = 500;

	private final Map<String, Long> transmittingPlayers = new ConcurrentHashMap<>();
	private final TileWhisperPlugin plugin;
	private final Client client;
	private final TileWhisperConfig config;

	@Inject
	public TileWhisperPlayerOverlay(TileWhisperPlugin plugin, Client client, TileWhisperConfig config)
	{
		this.plugin = plugin;
		this.client = client;
		this.config = config;
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPosition(OverlayPosition.DYNAMIC);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			return null;
		}

		WorldPoint localPos = client.getLocalPlayer().getWorldLocation();

		// Clean up expired transmitting players
		long now = System.currentTimeMillis();
		transmittingPlayers.entrySet().removeIf(entry -> now - entry.getValue() > TRANSMIT_TIMEOUT_MS);

		if (transmittingPlayers.isEmpty())
		{
			return null;
		}

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		for (Player player : client.getPlayers())
		{
			if (player == null || player.getName() == null)
			{
				continue;
			}

			if (!transmittingPlayers.containsKey(player.getName()))
			{
				continue;
			}

			if (player.getWorldLocation().getPlane() != localPos.getPlane())
			{
				continue;
			}

			int dx = Math.abs(player.getWorldLocation().getX() - localPos.getX());
			int dy = Math.abs(player.getWorldLocation().getY() - localPos.getY());
			if (Math.max(dx, dy) > config.maxRange())
			{
				continue;
			}

			drawSpeakingIndicator(graphics, player);
		}

		return null;
	}

	private void drawSpeakingIndicator(Graphics2D graphics, Player player)
	{
		LocalPoint localPoint = player.getLocalLocation();
		if (localPoint == null)
		{
			return;
		}

		// Project the point above the player's head to a 2D canvas position
		net.runelite.api.Point canvasPoint = Perspective.localToCanvas(
			client,
			localPoint,
			player.getWorldLocation().getPlane(),
			player.getModelHeight() + ICON_OFFSET_Y
		);

		if (canvasPoint == null)
		{
			return;
		}

		int x = canvasPoint.getX() - ICON_SIZE / 2;
		int y = canvasPoint.getY() - ICON_SIZE;

		drawMicIcon(graphics, x, y, ICON_SIZE);
	}

	private void drawMicIcon(Graphics2D graphics, int x, int y, int size)
	{
		// Background circle
		graphics.setColor(SPEAKING_BG_COLOR);
		graphics.fillOval(x, y, size, size);

		// Border
		graphics.setColor(SPEAKING_BORDER_COLOR);
		graphics.setStroke(new BasicStroke(1.5f));
		graphics.drawOval(x, y, size, size);

		// Microphone icon in white
		graphics.setColor(Color.WHITE);
		graphics.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

		int cx = x + size / 2;
		int cy = y + size / 2;

		// Mic capsule
		graphics.drawRoundRect(cx - 3, cy - 6, 6, 7, 4, 4);

		// Stand arc
		int arcX = cx - 4;
		int arcY = cy - 1;
		graphics.drawArc(arcX, arcY, 8, 6, 0, -180);

		// Stem
		graphics.drawLine(cx, arcY + 6, cx, cy + 5);

		// Base
		graphics.drawLine(cx - 3, cy + 5, cx + 3, cy + 5);
	}

	public void markPlayerTransmitting(String username)
	{
		if (username != null)
		{
			transmittingPlayers.put(username, System.currentTimeMillis());
		}
	}

	public void clearTransmittingPlayers()
	{
		transmittingPlayers.clear();
	}
}
