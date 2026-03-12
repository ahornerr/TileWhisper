package com.tilewhisper;

import com.google.inject.Inject;
import com.tilewhisper.network.NearbyPlayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class TileWhisperPanel extends PluginPanel
{
	private static final Color CONNECTED_COLOR = new Color(76, 175, 80);
	private static final Color DISCONNECTED_COLOR = new Color(244, 67, 54);
	private static final Color TEXT_COLOR = new Color(220, 220, 220);
	private static final Color SPEAKING_COLOR = new Color(0, 200, 83);
	private static final Color FRIEND_COLOR = new Color(255, 200, 50);
	private static final Color IGNORED_COLOR = new Color(120, 120, 120);
	private static final Color PANEL_BG = new Color(50, 50, 50);
	private static final Color PLAYER_BG = new Color(60, 60, 60);
	private static final Color PLAYER_BG_SPEAKING = new Color(40, 70, 50);
	private static final Color BUTTON_BG = new Color(75, 75, 75);
	private static final Color BUTTON_HOVER = new Color(100, 100, 100);

	private final TileWhisperPlugin plugin;
	private final JLabel connectionLabel;
	private final JLabel errorLabel;
	private final JPanel playersPanel;
	private final JLabel noPlayersLabel;

	private volatile List<NearbyPlayer> nearbyPlayers = new ArrayList<>();
	private WorldPoint localPosition;

	private final Map<String, Long> speakingTimestamps = new ConcurrentHashMap<>();
	private static final long SPEAKING_TIMEOUT_MS = 500;
	private javax.swing.Timer speakingTimer;

	private Set<String> friends = Collections.emptySet();
	private Set<String> ignored = Collections.emptySet();

	/** Persistent per-player row widgets — survive list refreshes. */
	private static final class PlayerRow
	{
		JPanel panel;
		JLabel nameLabel;
		JLabel distanceLabel;
		JButton muteButton;
		JSlider volumeSlider;
		boolean sliderDragging = false;
	}

	private final Map<String, PlayerRow> playerRows = new LinkedHashMap<>();

	@Inject
	public TileWhisperPanel(TileWhisperPlugin plugin)
	{
		this.plugin = plugin;
		setLayout(new BorderLayout(0, 0));
		setBackground(PANEL_BG);

		// ── Header ──────────────────────────────────────────────────────────
		JPanel headerPanel = new JPanel();
		headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
		headerPanel.setBackground(new Color(40, 40, 40));
		headerPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

		connectionLabel = new JLabel("Not Connected");
		connectionLabel.setForeground(DISCONNECTED_COLOR);
		connectionLabel.setFont(connectionLabel.getFont().deriveFont(Font.BOLD, 12f));
		connectionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		headerPanel.add(connectionLabel);

		errorLabel = new JLabel();
		errorLabel.setForeground(new Color(255, 120, 120));
		errorLabel.setFont(errorLabel.getFont().deriveFont(Font.ITALIC, 10f));
		errorLabel.setVisible(false);
		errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		headerPanel.add(errorLabel);

		headerPanel.add(Box.createVerticalStrut(6));

		JLabel nearbyHeader = new JLabel("NEARBY PLAYERS");
		nearbyHeader.setForeground(new Color(150, 150, 150));
		nearbyHeader.setFont(nearbyHeader.getFont().deriveFont(Font.BOLD, 9f));
		nearbyHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
		headerPanel.add(nearbyHeader);

		add(headerPanel, BorderLayout.NORTH);

		// ── Players list ─────────────────────────────────────────────────────
		playersPanel = new JPanel();
		playersPanel.setLayout(new BoxLayout(playersPanel, BoxLayout.Y_AXIS));
		playersPanel.setBackground(PANEL_BG);
		playersPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

		noPlayersLabel = new JLabel("No nearby players");
		noPlayersLabel.setForeground(new Color(130, 130, 130));
		noPlayersLabel.setFont(noPlayersLabel.getFont().deriveFont(Font.ITALIC, 11f));
		noPlayersLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		noPlayersLabel.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));

		JScrollPane scrollPane = new JScrollPane(playersPanel);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setBorder(null);
		scrollPane.setBackground(PANEL_BG);
		scrollPane.getViewport().setBackground(PANEL_BG);
		scrollPane.getVerticalScrollBar().setUnitIncrement(20);

		add(scrollPane, BorderLayout.CENTER);

		startSpeakingTimer();
	}

	private void startSpeakingTimer()
	{
		speakingTimer = new javax.swing.Timer(50, e -> tickSpeakingFade());
		speakingTimer.start();
	}

	private void tickSpeakingFade()
	{
		long now = System.currentTimeMillis();
		boolean any = speakingTimestamps.entrySet().removeIf(entry ->
			now - entry.getValue() > SPEAKING_TIMEOUT_MS);
		if (any)
		{
			refreshSpeakingState();
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
		SwingUtilities.invokeLater(this::reconcilePlayerRows);
	}

	public void updateNearbyPlayers(WorldPoint localPos)
	{
		this.localPosition = localPos;
		SwingUtilities.invokeLater(this::refreshDistanceLabels);
	}

	public void setFriends(Set<String> friends)
	{
		this.friends = friends;
		SwingUtilities.invokeLater(this::refreshNameStyles);
	}

	public void setIgnored(Set<String> ignored)
	{
		this.ignored = ignored;
		SwingUtilities.invokeLater(this::refreshNameStyles);
	}

	public void markPlayerSpeaking(String username)
	{
		speakingTimestamps.put(username, System.currentTimeMillis());
		SwingUtilities.invokeLater(() -> {
			PlayerRow row = playerRows.get(username);
			if (row != null)
			{
				applySpeakingStyle(row, username, true);
			}
		});
	}

	private boolean isPlayerSpeaking(String username)
	{
		Long ts = speakingTimestamps.get(username);
		return ts != null && (System.currentTimeMillis() - ts) < SPEAKING_TIMEOUT_MS;
	}

	// ── Panel reconciliation ─────────────────────────────────────────────────

	/**
	 * Add/remove rows to match the current nearbyPlayers list without
	 * destroying existing rows (preserves slider positions while dragging).
	 */
	private void reconcilePlayerRows()
	{
		List<NearbyPlayer> current = nearbyPlayers;
		Set<String> currentNames = new LinkedHashSet<>();
		for (NearbyPlayer p : current)
		{
			currentNames.add(p.getUsername());
		}

		// Remove rows for players no longer nearby
		Iterator<Map.Entry<String, PlayerRow>> it = playerRows.entrySet().iterator();
		while (it.hasNext())
		{
			Map.Entry<String, PlayerRow> entry = it.next();
			if (!currentNames.contains(entry.getKey()))
			{
				it.remove();
			}
		}

		// Add rows for new players
		for (NearbyPlayer p : current)
		{
			if (!playerRows.containsKey(p.getUsername()))
			{
				playerRows.put(p.getUsername(), createPlayerRow(p.getUsername()));
			}
		}

		// Rebuild playersPanel in correct order
		playersPanel.removeAll();

		if (playerRows.isEmpty())
		{
			playersPanel.add(noPlayersLabel);
		}
		else
		{
			boolean first = true;
			for (String name : currentNames)
			{
				PlayerRow row = playerRows.get(name);
				if (row == null) continue;

				if (!first)
				{
					JSeparator sep = new JSeparator();
					sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
					sep.setForeground(new Color(70, 70, 70));
					playersPanel.add(sep);
				}
				first = false;
				playersPanel.add(row.panel);
			}
		}

		// Refresh display attributes for all rows
		for (NearbyPlayer p : current)
		{
			PlayerRow row = playerRows.get(p.getUsername());
			if (row != null)
			{
				String username = p.getUsername();
				boolean speaking = isPlayerSpeaking(username);
				refreshDistanceLabel(row, p);
				applyNameStyle(row, username, speaking);
				applySpeakingStyle(row, username, speaking);
			}
		}

		playersPanel.revalidate();
		playersPanel.repaint();
	}

	private void refreshDistanceLabels()
	{
		for (NearbyPlayer p : nearbyPlayers)
		{
			PlayerRow row = playerRows.get(p.getUsername());
			if (row != null)
			{
				refreshDistanceLabel(row, p);
			}
		}
	}

	private void refreshDistanceLabel(PlayerRow row, NearbyPlayer player)
	{
		String dist = getDistanceText(player);
		if (!dist.equals(row.distanceLabel.getText()))
		{
			row.distanceLabel.setText(dist);
		}
	}

	private void refreshNameStyles()
	{
		for (NearbyPlayer p : nearbyPlayers)
		{
			PlayerRow row = playerRows.get(p.getUsername());
			if (row != null)
			{
				applyNameStyle(row, p.getUsername(), isPlayerSpeaking(p.getUsername()));
			}
		}
		playersPanel.repaint();
	}

	private void refreshSpeakingState()
	{
		for (NearbyPlayer p : nearbyPlayers)
		{
			String username = p.getUsername();
			PlayerRow row = playerRows.get(username);
			if (row != null)
			{
				boolean speaking = isPlayerSpeaking(username);
				applySpeakingStyle(row, username, speaking);
				applyNameStyle(row, username, speaking);
			}
		}
		playersPanel.repaint();
	}

	// ── Row creation ─────────────────────────────────────────────────────────

	private PlayerRow createPlayerRow(String username)
	{
		PlayerRow row = new PlayerRow();
		boolean muted = plugin.isPlayerMuted(username);
		float volume = plugin.getPlayerVolume(username);

		row.panel = new JPanel(new BorderLayout(8, 0));
		row.panel.setBackground(PLAYER_BG);
		row.panel.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 8));
		row.panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));

		// ── Left: name + distance ───────────────────────────────────────────
		JPanel leftPanel = new JPanel(new GridBagLayout());
		leftPanel.setOpaque(false);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0;

		row.nameLabel = new JLabel(username);
		row.nameLabel.setFont(row.nameLabel.getFont().deriveFont(Font.PLAIN, 12f));
		row.nameLabel.setForeground(TEXT_COLOR);
		leftPanel.add(row.nameLabel, gbc);

		gbc.gridy = 1;
		row.distanceLabel = new JLabel("? tiles");
		row.distanceLabel.setFont(row.distanceLabel.getFont().deriveFont(Font.PLAIN, 10f));
		row.distanceLabel.setForeground(new Color(160, 160, 160));
		leftPanel.add(row.distanceLabel, gbc);

		row.panel.add(leftPanel, BorderLayout.CENTER);

		// ── Right: mute button + volume slider ──────────────────────────────
		JPanel rightPanel = new JPanel(new GridBagLayout());
		rightPanel.setOpaque(false);

		GridBagConstraints rgbc = new GridBagConstraints();
		rgbc.gridx = 0;
		rgbc.gridy = 0;
		rgbc.anchor = GridBagConstraints.CENTER;
		rgbc.insets = new Insets(0, 0, 2, 0);

		row.muteButton = createMuteButton(username, muted, row);
		rightPanel.add(row.muteButton, rgbc);

		rgbc.gridy = 1;
		rgbc.fill = GridBagConstraints.HORIZONTAL;
		rgbc.insets = new Insets(0, 0, 0, 0);

		row.volumeSlider = new JSlider(0, 200, (int) (volume * 100));
		row.volumeSlider.setPreferredSize(new Dimension(72, 20));
		row.volumeSlider.setToolTipText("Volume: " + (int) (volume * 100) + "%");
		row.volumeSlider.setFocusable(false);
		row.volumeSlider.setOpaque(false);
		row.volumeSlider.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mousePressed(java.awt.event.MouseEvent e)
			{
				row.sliderDragging = true;
			}

			@Override
			public void mouseReleased(java.awt.event.MouseEvent e)
			{
				row.sliderDragging = false;
			}
		});
		row.volumeSlider.addChangeListener(e -> {
			if (!row.volumeSlider.getValueIsAdjusting())
			{
				float newVolume = row.volumeSlider.getValue() / 100.0f;
				plugin.setPlayerVolume(username, newVolume);
			}
			row.volumeSlider.setToolTipText("Volume: " + row.volumeSlider.getValue() + "%");
		});
		rightPanel.add(row.volumeSlider, rgbc);

		row.panel.add(rightPanel, BorderLayout.EAST);

		// Apply initial name styling
		applyNameStyle(row, username, false);

		return row;
	}

	private JButton createMuteButton(String username, boolean muted, PlayerRow row)
	{
		JButton button = new JButton(muted ? "\uD83D\uDD07" : "\uD83D\uDD0A");
		button.setPreferredSize(new Dimension(44, 22));
		button.setFocusPainted(false);
		button.setContentAreaFilled(false);
		button.setOpaque(true);
		button.setBackground(BUTTON_BG);
		button.setForeground(muted ? new Color(244, 67, 54) : TEXT_COLOR);
		button.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
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

	// ── Styling helpers ──────────────────────────────────────────────────────

	private void applyNameStyle(PlayerRow row, String username, boolean speaking)
	{
		boolean isFriend = friends.contains(username);
		boolean isIgnored = ignored.contains(username);

		String prefix = isFriend ? "\u2605 " : "";
		String suffix = isIgnored ? " \u26D4" : "";
		row.nameLabel.setText(prefix + username + suffix);

		String tooltip = username;
		if (isFriend) tooltip += " (friend)";
		if (isIgnored) tooltip += " (ignored)";
		row.nameLabel.setToolTipText(tooltip);
		row.distanceLabel.setForeground(isIgnored ? IGNORED_COLOR : new Color(160, 160, 160));

		if (speaking)
		{
			row.nameLabel.setForeground(SPEAKING_COLOR);
			row.nameLabel.setFont(row.nameLabel.getFont().deriveFont(Font.BOLD, 12f));
		}
		else if (isIgnored)
		{
			row.nameLabel.setForeground(IGNORED_COLOR);
			row.nameLabel.setFont(row.nameLabel.getFont().deriveFont(Font.PLAIN, 12f));
		}
		else if (isFriend)
		{
			row.nameLabel.setForeground(FRIEND_COLOR);
			row.nameLabel.setFont(row.nameLabel.getFont().deriveFont(Font.PLAIN, 12f));
		}
		else
		{
			row.nameLabel.setForeground(TEXT_COLOR);
			row.nameLabel.setFont(row.nameLabel.getFont().deriveFont(Font.PLAIN, 12f));
		}
	}

	private void applySpeakingStyle(PlayerRow row, String username, boolean speaking)
	{
		Color bg = speaking ? PLAYER_BG_SPEAKING : PLAYER_BG;
		if (!row.panel.getBackground().equals(bg))
		{
			row.panel.setBackground(bg);
		}
	}

	// ── Utilities ────────────────────────────────────────────────────────────

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
