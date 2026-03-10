package com.tilewhisper;

import com.google.inject.Inject;
import com.tilewhisper.network.NearbyPlayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TileWhisperPanel extends PluginPanel
{
	private static final Color CONNECTED_COLOR = new Color(76, 175, 80);
	private static final Color DISCONNECTED_COLOR = new Color(244, 67, 54);
	private static final Color TEXT_COLOR = new Color(255, 255, 255);
	private static final Color SPEAKING_COLOR = new Color(0, 200, 83);
	private static final Color FRIEND_COLOR = new Color(255, 200, 50); // Gold for friends
	private static final Color IGNORED_COLOR = new Color(120, 120, 120); // Dim grey for ignored
	private static final Color PANEL_BG = new Color(60, 60, 60);
	private static final Color BUTTON_BG = new Color(80, 80, 80);
	private static final Color BUTTON_HOVER = new Color(100, 100, 100);

	private final TileWhisperPlugin plugin;
	private final JLabel connectionLabel;
	private final JLabel errorLabel;
	private final JLabel headerLabel;
	private final JPanel playersPanel;
	private final JScrollPane scrollPane;

	private List<NearbyPlayer> nearbyPlayers = new ArrayList<>();
	private WorldPoint localPosition;

	// Track speaking players with timestamps for fade-out
	private final Map<String, Long> speakingTimestamps = new HashMap<>();
	private static final long SPEAKING_TIMEOUT_MS = 500;
	private javax.swing.Timer speakingTimer;

	// Friend set for showing friend indicators (updated from plugin on game tick)
	private Set<String> friends = Collections.emptySet();

	// Ignored players (updated from plugin on ignore list change)
	private Set<String> ignored = Collections.emptySet();

	@Inject
	public TileWhisperPanel(TileWhisperPlugin plugin)
	{
		this.plugin = plugin;
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

		errorLabel = new JLabel();
		errorLabel.setForeground(DISCONNECTED_COLOR);
		errorLabel.setFont(errorLabel.getFont().deriveFont(Font.ITALIC, 10f));
		errorLabel.setVisible(false);
		headerPanel.add(errorLabel, BorderLayout.CENTER);

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

		startSpeakingTimer();
	}

	private void startSpeakingTimer()
	{
		speakingTimer = new javax.swing.Timer(50, e -> updateSpeakingIndicators());
		speakingTimer.start();
	}

	private void updateSpeakingIndicators()
	{
		long now = System.currentTimeMillis();
		boolean needUpdate = false;

		for (Map.Entry<String, Long> entry : speakingTimestamps.entrySet())
		{
			if (now - entry.getValue() > SPEAKING_TIMEOUT_MS)
			{
				needUpdate = true;
				break;
			}
		}

		if (needUpdate)
		{
			speakingTimestamps.entrySet().removeIf(entry ->
				now - entry.getValue() > SPEAKING_TIMEOUT_MS
			);
			updatePlayerList();
		}
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

	public void showError(String message)
	{
		SwingUtilities.invokeLater(() -> {
			if (message != null && !message.isEmpty())
			{
				errorLabel.setText("<html><small>" + message + "</small></html>");
				errorLabel.setVisible(true);
			}
			else
			{
				errorLabel.setVisible(false);
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

	/** Called from plugin on game tick with the current friends list. */
	public void setFriends(Set<String> friends)
	{
		this.friends = friends;
		updatePlayerList();
	}

	/** Called from plugin when the ignore list changes. */
	public void setIgnored(Set<String> ignored)
	{
		this.ignored = ignored;
		updatePlayerList();
	}

	public void markPlayerSpeaking(String username)
	{
		speakingTimestamps.put(username, System.currentTimeMillis());
	}

	private boolean isPlayerSpeaking(String username)
	{
		Long timestamp = speakingTimestamps.get(username);
		return timestamp != null
			&& (System.currentTimeMillis() - timestamp) < SPEAKING_TIMEOUT_MS;
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
		String username = player.getUsername();
		boolean speaking = isPlayerSpeaking(username);
		boolean isFriend = friends.contains(username);
		boolean isIgnored = ignored.contains(username);
		boolean muted = plugin.isPlayerMuted(username);
		float volume = plugin.getPlayerVolume(username);

		JPanel panel = new JPanel(new BorderLayout(5, 0));
		panel.setBackground((isIgnored || speaking) ? PANEL_BG.brighter() : PANEL_BG);
		panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));

		// Left side: Name (with friend star) and distance
		JPanel leftPanel = new JPanel(new BorderLayout());
		leftPanel.setOpaque(false);

		String nameText = isFriend ? "\u2605 " + username : username;
		if (isIgnored)
		{
			nameText = nameText + " \u26D4"; // Add shield for ignored
		}

		JLabel nameLabel = new JLabel(nameText);
		if (speaking)
		{
			nameLabel.setForeground(SPEAKING_COLOR);
			nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
		}
		else if (isIgnored)
		{
			nameLabel.setForeground(IGNORED_COLOR);
			nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 12f));
		}
		else if (isFriend)
		{
			nameLabel.setForeground(FRIEND_COLOR);
			nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 12f));
		}
		else
		{
			nameLabel.setForeground(TEXT_COLOR);
			nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 12f));
		}

		String tooltipText = username;
		if (isFriend) tooltipText += " (friend)";
		if (isIgnored) tooltipText += " (ignored)";
		nameLabel.setToolTipText(tooltipText);
		leftPanel.add(nameLabel, BorderLayout.NORTH);

		JLabel distanceLabel = new JLabel(getDistanceText(player));
		distanceLabel.setForeground(isIgnored ? IGNORED_COLOR : new Color(180, 180, 180));
		distanceLabel.setFont(distanceLabel.getFont().deriveFont(Font.PLAIN, 11f));
		leftPanel.add(distanceLabel, BorderLayout.SOUTH);

		panel.add(leftPanel, BorderLayout.WEST);

		// Right side: Mute button, ignore button, volume slider
		JPanel rightPanel = new JPanel(new BorderLayout(5, 0));
		rightPanel.setOpaque(false);
		rightPanel.setPreferredSize(new Dimension(120, 60));

		JPanel buttonsPanel = new JPanel(new BorderLayout(5, 0));
		buttonsPanel.setOpaque(false);

		JButton muteButton = createMuteButton(username, muted);
		buttonsPanel.add(muteButton, BorderLayout.WEST);

		JButton ignoreButton = createIgnoreButton(username, isIgnored);
		buttonsPanel.add(ignoreButton, BorderLayout.EAST);

		rightPanel.add(buttonsPanel, BorderLayout.NORTH);

		JSlider volumeSlider = new JSlider(0, 200, (int) (volume * 100));
		volumeSlider.setMajorTickSpacing(100);
		volumeSlider.setMinorTickSpacing(50);
		volumeSlider.setPaintTicks(true);
		volumeSlider.setPreferredSize(new Dimension(80, 30));
		volumeSlider.setToolTipText("Volume: " + (int) (volume * 100) + "%");
		volumeSlider.addChangeListener(e -> {
			float newVolume = volumeSlider.getValue() / 100.0f;
			plugin.setPlayerVolume(username, newVolume);
			volumeSlider.setToolTipText("Volume: " + volumeSlider.getValue() + "%");
		});

		JPanel sliderPanel = new JPanel(new BorderLayout());
		sliderPanel.setOpaque(false);
		JLabel volumeLabel = new JLabel("Vol");
		volumeLabel.setForeground(new Color(150, 150, 150));
		volumeLabel.setFont(volumeLabel.getFont().deriveFont(Font.PLAIN, 9f));
		sliderPanel.add(volumeLabel, BorderLayout.NORTH);
		sliderPanel.add(volumeSlider, BorderLayout.CENTER);

		rightPanel.add(sliderPanel, BorderLayout.CENTER);

		panel.add(rightPanel, BorderLayout.EAST);

		return panel;
	}

	private JButton createMuteButton(String username, boolean muted)
	{
		JButton button = new JButton(muted ? "\uD83D\uDD07" : "\uD83D\uDD0A");
		button.setPreferredSize(new Dimension(50, 24));
		button.setFocusPainted(false);
		button.setContentAreaFilled(false);
		button.setOpaque(true);
		button.setBackground(BUTTON_BG);
		button.setForeground(muted ? new Color(244, 67, 54) : TEXT_COLOR);
		button.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
		button.setToolTipText(muted ? "Unmute player" : "Mute player");

		button.addActionListener((ActionEvent e) -> {
			boolean newMuted = !plugin.isPlayerMuted(username);
			plugin.setPlayerMuted(username, newMuted);
			button.setText(newMuted ? "\uD83D\uDD07" : "\uD83D\uDD0A");
			button.setForeground(newMuted ? new Color(244, 67, 54) : TEXT_COLOR);
			button.setToolTipText(newMuted ? "Unmute player" : "Mute player");
		});

		button.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseEntered(java.awt.event.MouseEvent e)
			{
				button.setBackground(BUTTON_HOVER);
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent e)
			{
				button.setBackground(BUTTON_BG);
			}
		});

		return button;
	}

	private JButton createIgnoreButton(String username, boolean ignored)
	{
		JButton button = new JButton(ignored ? "\uD83D\uDD13" : "\uD83D\uDED4"); // 🚫 or 🛡️
		button.setPreferredSize(new Dimension(50, 24));
		button.setFocusPainted(false);
		button.setContentAreaFilled(false);
		button.setOpaque(true);
		button.setBackground(BUTTON_BG);
		button.setForeground(ignored ? new Color(244, 67, 54) : TEXT_COLOR);
		button.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
		button.setToolTipText(ignored ? "Unignore player" : "Ignore player (persistent)");

		button.addActionListener((ActionEvent e) -> {
			boolean newIgnored = !plugin.isPlayerIgnored(username);
			plugin.setPlayerIgnored(username, newIgnored);
			button.setText(newIgnored ? "\uD83D\uDD13" : "\uD83D\uDED4");
			button.setForeground(newIgnored ? new Color(244, 67, 54) : TEXT_COLOR);
			button.setToolTipText(newIgnored ? "Unignore player" : "Ignore player (persistent)");
			updatePlayerList(); // Refresh panel to show ignored status
		});

		button.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseEntered(java.awt.event.MouseEvent e)
			{
				button.setBackground(BUTTON_HOVER);
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent e)
			{
				button.setBackground(BUTTON_BG);
			}
		});

		return button;
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

	public void shutdown()
	{
		if (speakingTimer != null)
		{
			speakingTimer.stop();
			speakingTimer = null;
		}
	}
}
