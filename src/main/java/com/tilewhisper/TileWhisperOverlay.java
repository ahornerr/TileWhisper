package com.tilewhisper;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.*;

public class TileWhisperOverlay extends Overlay
{
	private static final Color TRANSMIT_COLOR = new Color(220, 50, 50, 220);
	private static final Color TRANSMIT_TEXT_COLOR = Color.WHITE;
	private static final int CIRCLE_SIZE = 12;
	private static final int PADDING = 10;

	private final TileWhisperPlugin plugin;

	@Inject
	public TileWhisperOverlay(TileWhisperPlugin plugin)
	{
		this.plugin = plugin;
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.isPttActive())
		{
			return null;
		}

		// Draw transmitting indicator
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int x = PADDING;
		int y = PADDING;

		// Draw filled circle
		graphics.setColor(TRANSMIT_COLOR);
		graphics.fillOval(x, y, CIRCLE_SIZE, CIRCLE_SIZE);

		// Draw text
		graphics.setColor(TRANSMIT_TEXT_COLOR);
		graphics.setFont(new Font("SansSerif", Font.BOLD, 12));
		graphics.drawString("TRANSMITTING", x + CIRCLE_SIZE + 6, y + CIRCLE_SIZE - 1);

		FontMetrics fm = graphics.getFontMetrics();
		int textWidth = fm.stringWidth("TRANSMITTING");

		return new Dimension(x + CIRCLE_SIZE + 6 + textWidth + PADDING, y + CIRCLE_SIZE + PADDING);
	}
}
