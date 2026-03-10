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
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.input.KeyListener;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
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
					boolean shouldSend = false;
					if (config.voiceActivation() == TileWhisperConfig.VoiceActivationMode.PTT)
					{
						shouldSend = pttActive;
					}
					else
					{
						// In VAD mode, AudioCapture itself decides when to transmit
						shouldSend = true;
					}

					// Update transmitting flag for overlay
					long now = System.currentTimeMillis();
					if (shouldSend)
					{
						lastTransmitTime = now;
					}

					WorldPoint localPos = client.getLocalPlayer() != null
						? client.getLocalPlayer().getWorldLocation()
						: null;

					if (localPos != null && shouldSend && networkManager != null)
					{
						networkManager.sendAudio(
							client.getWorld(),
							localPos.getX(),
							localPos.getY(),
							localPos.getPlane(),
							client.getLocalPlayer().getName(),
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

			// Create a simple icon
			BufferedImage icon = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2d = icon.createGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setColor(new Color(76, 175, 80)); // Green
			g2d.fillOval(2, 2, 28, 28);
			g2d.setColor(Color.WHITE);
			g2d.setStroke(new BasicStroke(2f));
			g2d.drawOval(2, 2, 28, 28);
			g2d.dispose();

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

		log.info("TileWhisper plugin stopped");
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (networkManager == null)
		{
			return;
		}

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (client.getLocalPlayer() == null)
		{
			return;
		}

		WorldPoint localPos = client.getLocalPlayer().getWorldLocation();
		int world = client.getWorld();

		networkManager.sendPresence(
			world,
			localPos.getX(),
			localPos.getY(),
			localPos.getPlane(),
			client.getLocalPlayer().getName()
		);

		if (panel != null)
		{
			panel.updateNearbyPlayers(localPos);
		}
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

		clientThread.invokeLater(() -> {
			if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
			{
				return;
			}

			WorldPoint localPos = client.getLocalPlayer().getWorldLocation();

			if (packet.getWorld() != client.getWorld() || packet.getPlane() != localPos.getPlane())
			{
				return;
			}

			int dx = Math.abs(packet.getX() - localPos.getX());
			int dy = Math.abs(packet.getY() - localPos.getY());
			int distance = Math.max(dx, dy);
			float volumeFactor = Math.max(0f, 1.0f - (float) distance / config.maxRange());

			if (volumeFactor > 0 && audioPlayback != null)
			{
				audioPlayback.playAudio(packet.getUsername(), audioData, volumeFactor);
			}

			if (playerOverlay != null && volumeFactor > 0)
			{
				playerOverlay.markPlayerTransmitting(packet.getUsername());
			}

			// Update panel speaking indicator
			if (panel != null && volumeFactor > 0)
			{
				panel.markPlayerSpeaking(packet.getUsername());
			}
		});
	}

	private void onNearbyPlayersReceived(List<NearbyPlayer> players)
	{
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
