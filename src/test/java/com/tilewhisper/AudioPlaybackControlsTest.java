package com.tilewhisper;

import com.tilewhisper.audio.AudioPlayback;
import org.junit.Test;

import static org.junit.Assert.*;

public class AudioPlaybackControlsTest
{
	// ========================================================================
	// Per-player volume control
	// ========================================================================

	@Test
	public void setPlayerVolume_returnsValue()
	{
		AudioPlayback playback = new AudioPlayback(100);
		playback.setPlayerVolume("Player1", 1.5f);
		assertEquals(1.5f, playback.getPlayerVolume("Player1"), 0.01f);
	}

	@Test
	public void setPlayerVolume_clampsToMin()
	{
		AudioPlayback playback = new AudioPlayback(100);
		playback.setPlayerVolume("Player1", -5.0f);
		assertEquals(0.0f, playback.getPlayerVolume("Player1"), 0.01f);
	}

	@Test
	public void setPlayerVolume_clampsToMax()
	{
		AudioPlayback playback = new AudioPlayback(100);
		playback.setPlayerVolume("Player1", 5.0f);
		assertEquals(2.0f, playback.getPlayerVolume("Player1"), 0.01f);
	}

	@Test
	public void setPlayerVolume_independentPlayersNotAffected()
	{
		AudioPlayback playback = new AudioPlayback(100);
		playback.setPlayerVolume("Player1", 0.5f);
		playback.setPlayerVolume("Player2", 1.5f);

		assertEquals(0.5f, playback.getPlayerVolume("Player1"), 0.01f);
		assertEquals(1.5f, playback.getPlayerVolume("Player2"), 0.01f);
	}

	// ========================================================================
	// Per-player mute control
	// ========================================================================

	@Test
	public void setPlayerMuted_canBeMuted()
	{
		AudioPlayback playback = new AudioPlayback(100);
		playback.setPlayerMuted("Player1", true);
		assertTrue("Should be muted", playback.isPlayerMuted("Player1"));
	}

	@Test
	public void setPlayerMuted_canBeUnmuted()
	{
		AudioPlayback playback = new AudioPlayback(100);
		playback.setPlayerMuted("Player1", true);
		playback.setPlayerMuted("Player1", false);
		assertFalse("Should not be muted", playback.isPlayerMuted("Player1"));
	}

	@Test
	public void isPlayerMuted_returnsFalseForUnknownPlayer()
	{
		AudioPlayback playback = new AudioPlayback(100);
		assertFalse("Unknown player should not be muted", playback.isPlayerMuted("Nobody"));
	}

	@Test
	public void isPlayerMuted_multiplePlayersIndependent()
	{
		AudioPlayback playback = new AudioPlayback(100);
		playback.setPlayerMuted("Player1", true);
		playback.setPlayerMuted("Player2", false);

		assertTrue("Player1 should be muted", playback.isPlayerMuted("Player1"));
		assertFalse("Player2 should not be muted", playback.isPlayerMuted("Player2"));
	}

	// ========================================================================
	// Volume and mute together
	// ========================================================================

	@Test
	public void volumeAndMute_canCoexist()
	{
		AudioPlayback playback = new AudioPlayback(100);
		playback.setPlayerVolume("Player1", 0.5f);
		playback.setPlayerMuted("Player1", true);

		assertEquals(0.5f, playback.getPlayerVolume("Player1"), 0.01f);
		assertTrue("Should still be muted", playback.isPlayerMuted("Player1"));
	}

	@Test
	public void unmuted_preservesVolumeSetting()
	{
		AudioPlayback playback = new AudioPlayback(100);
		playback.setPlayerVolume("Player1", 1.8f);
		playback.setPlayerMuted("Player1", true);
		playback.setPlayerMuted("Player1", false);

		assertEquals("Volume should persist", 1.8f, playback.getPlayerVolume("Player1"), 0.01f);
		assertFalse("Should not be muted", playback.isPlayerMuted("Player1"));
	}

	// ========================================================================
	// Default values
	// ========================================================================

	@Test
	public void getDefaultVolume_forUnknownPlayer_returns1_0()
	{
		AudioPlayback playback = new AudioPlayback(100);
		assertEquals(1.0f, playback.getPlayerVolume("Nobody"), 0.01f);
	}

	@Test
	public void getDefaultMuted_forUnknownPlayer_returnsFalse()
	{
		AudioPlayback playback = new AudioPlayback(100);
		assertFalse("Unknown player should not be muted", playback.isPlayerMuted("Nobody"));
	}

	// ========================================================================
	// Player cleanup
	// ========================================================================

	@Test
	public void cleanupPlayer_removesVolumeAndMute()
	{
		AudioPlayback playback = new AudioPlayback(100);
		playback.setPlayerVolume("Player1", 0.7f);
		playback.setPlayerMuted("Player1", true);

		playback.cleanupPlayer("Player1");

		assertEquals("Volume should reset to default", 1.0f, playback.getPlayerVolume("Player1"), 0.01f);
		assertFalse("Mute should be cleared", playback.isPlayerMuted("Player1"));
	}

	@Test
	public void cleanupPlayer_otherPlayersNotAffected()
	{
		AudioPlayback playback = new AudioPlayback(100);
		playback.setPlayerVolume("Player1", 0.7f);
		playback.setPlayerVolume("Player2", 1.3f);

		playback.cleanupPlayer("Player1");

		assertEquals("Player2 volume should persist", 1.3f, playback.getPlayerVolume("Player2"), 0.01f);
		assertEquals("Player1 volume should reset", 1.0f, playback.getPlayerVolume("Player1"), 0.01f);
	}
}
