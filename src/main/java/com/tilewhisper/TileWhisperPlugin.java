package com.tilewhisper;

import com.google.inject.Inject;
import com.tilewhisper.audio.AudioCapture;
import com.tilewhisper.audio.AudioPlayback;
import com.tilewhisper.audio.OpusCodec;
import com.tilewhisper.network.NetworkManager;
import com.tilewhisper.network.NearbyPlayer;
import com.tilewhisper.network.VoicePacket;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Friend;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.GameState;
import net.runelite.api.Ignore;
import net.runelite.api.NameableContainer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.input.KeyListener;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

import com.google.inject.Provides;
import net.runelite.client.eventbus.Subscribe;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.List;

@Slf4j
@PluginDescriptor(
	name = "TileWhisper",
	description = "Proximity voice chat for OSRS",
	tags = {"voice", "chat", "proximity", "audio"}
)
public class TileWhisperPlugin extends Plugin implements KeyListener
{
	@Inject
	private Client client;

	@Inject
	private TileWhisperConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private KeyManager keyManager;

	@Inject
	private ClientThread clientThread;

	@Provides
	TileWhisperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TileWhisperConfig.class);
	}

	private NetworkManager networkManager;
	private AudioCapture audioCapture;
	private AudioPlayback audioPlayback;
	private TileWhisperOverlay overlay;
	private TileWhisperPlayerOverlay playerOverlay;
	private TileWhisperPanel panel;
	private NavigationButton navButton;

	volatile boolean pttActive = false;
	private volatile long lastTransmitTime = 0;
	private static final long TRANSMIT_INDICATOR_TIMEOUT_MS = 200;

	// Cached player state — written on client thread (onGameTick), read from audio threads
	private volatile int cachedWorld;
	private volatile int cachedX;
	private volatile int cachedY;
	private volatile int cachedPlane;
	private volatile String cachedUsername;
	private volatile java.util.Set<String> cachedFriends = java.util.Collections.emptySet();
	private volatile java.util.Set<String> cachedIgnored = java.util.Collections.emptySet();

	// Track nearby players to detect departures and validate incoming audio senders.
	// Updated atomically (volatile reference to an immutable set) so onAudioReceived
	// can safely read it from the WebSocket callback thread without locking.
	private volatile java.util.Set<String> nearbyUsernames = java.util.Collections.emptySet();

	@Override
	protected void startUp()
	{
		log.info("TileWhisper plugin starting up");

		try
		{
			// Load Opus library and check result
			boolean opusLoaded = OpusCodec.loadLibrary();
			if (!opusLoaded)
			{
				log.error("Failed to load Opus native library — audio will not work");
			}

			// Initialize panel first (so we can show errors)
			panel = new TileWhisperPanel(this);

			if (!opusLoaded)
			{
				panel.showError("Opus codec unavailable — audio features disabled");
			}

			// Initialize audio (non-fatal — may fail in headless/dev environments)
			// Use catch Throwable to cover JNA/native errors like NoClassDefFoundError
			// that occur on platforms where the Opus native lib isn't available.
			try
			{
				audioPlayback = new AudioPlayback(config.outputVolume());
				audioPlayback.start();
			}
			catch (Throwable e)
			{
				log.warn("Audio playback unavailable: {}", e.getMessage());
				audioPlayback = null;
			}

			try
			{
				audioCapture = new AudioCapture((encoded) -> {
					// AudioCapture already filters by PTT/VAD before invoking this callback.
					// Just forward to network layer using cached player state.
					String username = cachedUsername;
					if (username != null && networkManager != null)
					{
						lastTransmitTime = System.currentTimeMillis();
						networkManager.sendAudio(
							cachedWorld,
							cachedX,
							cachedY,
							cachedPlane,
							username,
							encoded
						);
					}
				}, config.inputVolume(), config.voiceActivation(), config.vadThreshold());
				audioCapture.start();
				log.info("Audio capture started successfully");
			}
			catch (Throwable e)
			{
				log.warn("Audio capture unavailable: {}", e.getMessage());
				audioCapture = null;
			}

			// Initialize network
			networkManager = new NetworkManager(
				this::onNearbyPlayersReceived,
				this::onAudioReceived,
				this::onConnectionChanged
			);
			networkManager.connect(config.serverUrl());

			// Initialize overlays
			overlay = new TileWhisperOverlay(this);
			overlayManager.add(overlay);

			playerOverlay = new TileWhisperPlayerOverlay(this, client, config);
			overlayManager.add(playerOverlay);

			// Load icon from resources
			final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");

			navButton = NavigationButton.builder()
				.tooltip("TileWhisper")
				.icon(icon)
				.priority(5)
				.panel(panel)
				.build();
			clientToolbar.addNavigation(navButton);

			// Register key listener
			keyManager.registerKeyListener(this);

			log.info("TileWhisper plugin started");
		}
		catch (Exception e)
		{
			log.error("Failed to start TileWhisper plugin", e);
			shutDown();
		}
	}

	@Override
	protected void shutDown()
	{
		log.info("TileWhisper plugin shutting down");

		if (keyManager != null)
		{
			keyManager.unregisterKeyListener(this);
		}

		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}

		if (playerOverlay != null)
		{
			overlayManager.remove(playerOverlay);
			playerOverlay = null;
		}

		if (overlay != null)
		{
			overlayManager.remove(overlay);
			overlay = null;
		}

		if (audioCapture != null)
		{
			audioCapture.close();
			audioCapture = null;
		}

		if (audioPlayback != null)
		{
			audioPlayback.close();
			audioPlayback = null;
		}

		if (networkManager != null)
		{
			networkManager.close();
			networkManager = null;
		}

		if (panel != null)
		{
			panel.shutdown();
			panel = null;
		}

		cachedUsername = null;
		nearbyUsernames = java.util.Collections.emptySet();

		log.info("TileWhisper plugin stopped");
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			cachedUsername = null;
			return;
		}

		if (client.getLocalPlayer() == null)
		{
			cachedUsername = null;
			return;
		}

		// Cache player state for audio threads (thread-safe volatile writes)
		WorldPoint localPos = client.getLocalPlayer().getWorldLocation();
		cachedWorld = client.getWorld();
		cachedX = localPos.getX();
		cachedY = localPos.getY();
		cachedPlane = localPos.getPlane();
		cachedUsername = client.getLocalPlayer().getName();

		if (networkManager != null)
		{
			networkManager.sendPresence(
				cachedWorld,
				cachedX,
				cachedY,
				cachedPlane,
				cachedUsername
			);
		}

		if (panel != null)
		{
			panel.updateNearbyPlayers(localPos);
		}

		getAndNotifyFriends();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"tilewhisper".equals(event.getGroup()))
		{
			return;
		}

		if (audioCapture != null)
		{
			audioCapture.setVoiceActivation(config.voiceActivation(), config.vadThreshold());
			audioCapture.setInputVolume(config.inputVolume());
		}

		if (audioPlayback != null)
		{
			audioPlayback.setOutputVolume(config.outputVolume());
		}

		getAndNotifyFriends();
	}

	private void getAndNotifyFriends()
	{
		if (panel == null)
		{
			return;
		}

		java.util.Set<String> friendNames = new java.util.HashSet<>();
		java.util.Set<String> ignoredNames = new java.util.HashSet<>();

		// Friends list
		NameableContainer<Friend> friendsContainer = client.getFriendContainer();
		if (friendsContainer != null)
		{
			for (int i = 0; i < friendsContainer.getCount(); i++)
			{
				Friend friend = friendsContainer.getMembers()[i];
				if (friend != null && friend.getName() != null)
				{
					friendNames.add(friend.getName());
				}
			}
		}

		// Friends chat members (if voiceRangeMode includes FC)
		if (config.voiceRangeMode() == TileWhisperConfig.VoiceRangeMode.FRIENDS_CHAT
			|| config.voiceRangeMode() == TileWhisperConfig.VoiceRangeMode.BOTH)
		{
			FriendsChatManager fcManager = client.getFriendsChatManager();
			if (fcManager != null)
			{
				for (FriendsChatMember member : fcManager.getMembers())
				{
					if (member != null && member.getName() != null)
					{
						friendNames.add(member.getName());
					}
				}
			}
		}

		// Ignore list (built-in OSRS ignore list)
		NameableContainer<Ignore> ignoreContainer = client.getIgnoreContainer();
		if (ignoreContainer != null)
		{
			for (int i = 0; i < ignoreContainer.getCount(); i++)
			{
				Ignore ignored = ignoreContainer.getMembers()[i];
				if (ignored != null && ignored.getName() != null)
				{
					ignoredNames.add(ignored.getName());
				}
			}
		}

		panel.setFriends(friendNames);
		panel.setIgnored(ignoredNames);
		cachedFriends = friendNames;
		cachedIgnored = ignoredNames;
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		if (config.voiceActivation() == TileWhisperConfig.VoiceActivationMode.PTT
			&& config.pushToTalkKey().matches(e))
		{
			pttActive = true;
			if (audioCapture != null)
			{
				audioCapture.setPushToTalkActive(true);
			}
		}
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
		if (config.voiceActivation() == TileWhisperConfig.VoiceActivationMode.PTT
			&& config.pushToTalkKey().matches(e))
		{
			pttActive = false;
			if (audioCapture != null)
			{
				audioCapture.setPushToTalkActive(false);
			}
		}
	}

	@Override
	public void keyTyped(KeyEvent e)
	{
		// Not used
	}

	private void onAudioReceived(VoicePacket packet, byte[] audioData)
	{
		if (config.muteAudio())
		{
			return;
		}

		// Use cached player state to compute volume — runs directly on the
		// WebSocket thread so audio is never bottlenecked by the client thread.
		String localUsername = cachedUsername;
		if (localUsername == null)
		{
			return; // Not logged in yet
		}

		if (packet.getWorld() != cachedWorld || packet.getPlane() != cachedPlane)
		{
			return;
		}

		String senderName = packet.getUsername();
		java.util.Set<String> friends = cachedFriends;
		java.util.Set<String> ignored = cachedIgnored;

		// Cross-validate sender against the server-provided nearby list. Only enforce
		// once we've received at least one nearby update (non-empty set). This prevents
		// audio injection from senders that the game client has not confirmed as nearby.
		java.util.Set<String> nearby = nearbyUsernames;
		if (!nearby.isEmpty() && !nearby.contains(senderName))
		{
			return;
		}

		if (!shouldReceiveAudio(senderName, friends, config.friendsOnly(), config.voiceRangeMode(), ignored))
		{
			return;
		}

		// FC mode sends at full volume regardless of tile distance
		boolean isFCOnlyMode = config.voiceRangeMode() == TileWhisperConfig.VoiceRangeMode.FRIENDS_CHAT
			&& friends != null && friends.contains(senderName);

		float volumeFactor;
		if (isFCOnlyMode)
		{
			volumeFactor = 1.0f;
		}
		else
		{
			int dx = Math.abs(packet.getX() - cachedX);
			int dy = Math.abs(packet.getY() - cachedY);
			int distance = Math.max(dx, dy);
			volumeFactor = Math.max(0f, 1.0f - (float) distance / config.maxRange());
		}

		if (volumeFactor > 0 && audioPlayback != null)
		{
			audioPlayback.playAudio(senderName, audioData, volumeFactor);
		}

		if (volumeFactor > 0)
		{
			if (playerOverlay != null)
			{
				playerOverlay.markPlayerTransmitting(senderName);
			}

			if (panel != null)
			{
				panel.markPlayerSpeaking(senderName);
			}
		}
	}

	/**
	 * Decides whether audio from {@code senderName} should be played.
	 * Package-private and static so it can be unit-tested without a RuneLite runtime.
	 */
	static boolean shouldReceiveAudio(
		String senderName,
		java.util.Set<String> friends,
		boolean friendsOnly,
		TileWhisperConfig.VoiceRangeMode voiceRangeMode,
		java.util.Set<String> ignored)
	{
		if (senderName == null || friends == null)
		{
			return false;
		}

		// Ignore list takes priority over everything
		if (ignored != null && ignored.contains(senderName))
		{
			return false;
		}

		boolean senderIsFriend = friends.contains(senderName);

		// Friends-only gate: applies regardless of voice range mode
		if (friendsOnly && !senderIsFriend)
		{
			return false;
		}

		// FRIENDS_CHAT mode: only allow FC members (stored in the friends set)
		if (voiceRangeMode == TileWhisperConfig.VoiceRangeMode.FRIENDS_CHAT && !senderIsFriend)
		{
			return false;
		}

		return true;
	}

	/** Backward-compatible overload (no ignore list parameter) */
	static boolean shouldReceiveAudio(
		String senderName,
		java.util.Set<String> friends,
		boolean friendsOnly,
		TileWhisperConfig.VoiceRangeMode voiceRangeMode)
	{
		return shouldReceiveAudio(senderName, friends, friendsOnly, voiceRangeMode, null);
	}

	private void onNearbyPlayersReceived(List<NearbyPlayer> players)
	{
		// Detect departed players and clean up their audio state
		java.util.Set<String> newUsernames = new java.util.HashSet<>();
		for (NearbyPlayer p : players)
		{
			if (p.getUsername() != null)
			{
				newUsernames.add(p.getUsername());
			}
		}
		java.util.Set<String> oldUsernames = nearbyUsernames;
		for (String username : oldUsernames)
		{
			if (!newUsernames.contains(username) && audioPlayback != null)
			{
				audioPlayback.cleanupPlayer(username);
			}
		}
		// Atomic update — onAudioReceived reads this reference without locking
		nearbyUsernames = java.util.Collections.unmodifiableSet(newUsernames);

		clientThread.invokeLater(() -> {
			if (panel != null)
			{
				panel.setNearbyPlayers(players);
			}
		});
	}

	private void onConnectionChanged(boolean connected)
	{
		clientThread.invokeLater(() -> {
			if (panel != null)
			{
				panel.setConnected(connected);
			}

			if (!connected && playerOverlay != null)
			{
				playerOverlay.clearTransmittingPlayers();
			}
		});
	}

	public boolean isPttActive()
	{
		return pttActive;
	}

	public boolean isTransmitting()
	{
		if (config.voiceActivation() == TileWhisperConfig.VoiceActivationMode.PTT)
		{
			return pttActive;
		}
		return (System.currentTimeMillis() - lastTransmitTime) < TRANSMIT_INDICATOR_TIMEOUT_MS;
	}

	// ---- Per-player volume/mute controls (called from TileWhisperPanel) ----

	public void setPlayerVolume(String username, float volume)
	{
		if (audioPlayback != null)
		{
			audioPlayback.setPlayerVolume(username, volume);
		}
	}

	public float getPlayerVolume(String username)
	{
		return audioPlayback != null ? audioPlayback.getPlayerVolume(username) : 1.0f;
	}

	public void setPlayerMuted(String username, boolean muted)
	{
		if (audioPlayback != null)
		{
			audioPlayback.setPlayerMuted(username, muted);
		}
	}

	public boolean isPlayerMuted(String username)
	{
		return audioPlayback != null && audioPlayback.isPlayerMuted(username);
	}
}
