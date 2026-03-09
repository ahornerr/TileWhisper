package com.tilewhisper;

import com.google.inject.Inject;
import com.tilewhisper.network.NearbyPlayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TileWhisperPanel extends PluginPanel
{
	private static final Color CONNECTED_COLOR = new Color(76, 175, 80);
	private static final Color DISCONNECTED_COLOR = new Color(244, 67, 54);
	private static final Color TEXT_COLOR = new Color(255, 255, 255);

	private final JLabel connectionLabel;
	private final JLabel headerLabel;
	private final JPanel playersPanel;
	private final JScrollPane scrollPane;

	private List<NearbyPlayer> nearbyPlayers = new ArrayList<>();
	private WorldPoint localPosition;

	@Inject
	public TileWhisperPanel()
	{
		setLayout(new BorderLayout(0, 5));
		setBackground(Color.DARK_GRAY);

		// Header section
		JPanel headerPanel = new JPanel(new BorderLayout());
		headerPanel.setBackground(Color.DARK_GRAY);
		headerPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		connectionLabel = new JLabel("Not Connected");
		connectionLabel.setForeground(DISCONNECTED_COLOR);
		connectionLabel.setFont(connectionLabel.getFont().deriveFont(Font.BOLD, 12f));
		headerPanel.add(connectionLabel, BorderLayout.NORTH);

		headerLabel = new JLabel("<html><body style='text-align:center'><b>Nearby Players</b></body></html>");
		headerLabel.setForeground(TEXT_COLOR);
		headerLabel.setHorizontalAlignment(SwingConstants.CENTER);
		headerPanel.add(headerLabel, BorderLayout.SOUTH);

		add(headerPanel, BorderLayout.NORTH);

		// Players list
		playersPanel = new JPanel();
		playersPanel.setLayout(new BoxLayout(playersPanel, BoxLayout.Y_AXIS));
		playersPanel.setBackground(Color.DARK_GRAY);
		playersPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		scrollPane = new JScrollPane(playersPanel);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setBorder(null);
		scrollPane.setBackground(Color.DARK_GRAY);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);

		add(scrollPane, BorderLayout.CENTER);
	}

	public void setConnected(boolean connected)
	{
		SwingUtilities.invokeLater(() -> {
			if (connected)
			{
				connectionLabel.setText("Connected");
				connectionLabel.setForeground(CONNECTED_COLOR);
			}
			else
			{
				connectionLabel.setText("Not Connected");
				connectionLabel.setForeground(DISCONNECTED_COLOR);
			}
		});
	}

	public void setNearbyPlayers(List<NearbyPlayer> players)
	{
		this.nearbyPlayers = players;
		updatePlayerList();
	}

	public void updateNearbyPlayers(WorldPoint localPos)
	{
		this.localPosition = localPos;
		updatePlayerList();
	}

	private void updatePlayerList()
	{
		SwingUtilities.invokeLater(() -> {
			playersPanel.removeAll();

			if (nearbyPlayers.isEmpty())
			{
				JLabel noPlayersLabel = new JLabel("No nearby players");
				noPlayersLabel.setForeground(new Color(150, 150, 150));
				noPlayersLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
				playersPanel.add(noPlayersLabel);
			}
			else
			{
				for (NearbyPlayer player : nearbyPlayers)
				{
					JPanel playerPanel = createPlayerPanel(player);
					playersPanel.add(playerPanel);
					playersPanel.add(Box.createVerticalStrut(3));
				}
			}

			playersPanel.revalidate();
			playersPanel.repaint();
		});
	}

	private JPanel createPlayerPanel(NearbyPlayer player)
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(new Color(60, 60, 60));
		panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

		JLabel nameLabel = new JLabel(player.getUsername());
		nameLabel.setForeground(TEXT_COLOR);
		nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 12f));
		panel.add(nameLabel, BorderLayout.WEST);

		JLabel distanceLabel = new JLabel(getDistanceText(player));
		distanceLabel.setForeground(new Color(180, 180, 180));
		distanceLabel.setFont(distanceLabel.getFont().deriveFont(Font.PLAIN, 11f));
		panel.add(distanceLabel, BorderLayout.EAST);

		return panel;
	}

	private String getDistanceText(NearbyPlayer player)
	{
		if (localPosition == null)
		{
			return "? tiles";
		}

		int dx = Math.abs(player.getX() - localPosition.getX());
		int dy = Math.abs(player.getY() - localPosition.getY());
		int distance = Math.max(dx, dy);

		return distance + " tile" + (distance == 1 ? "" : "s");
	}
}
