package com.tilewhisper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

import java.awt.event.KeyEvent;

@ConfigGroup("tilewhisper")
public interface TileWhisperConfig extends Config
{
	enum VoiceActivationMode
	{
		PTT("Push-to-Talk (hold key)"),
		VAD("Voice Activity Detection");

		public final String label;

		VoiceActivationMode(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigItem(
		keyName = "voiceActivation",
		name = "Voice Activation",
		description = "Choose how voice is activated"
	)
	default VoiceActivationMode voiceActivation()
	{
		return VoiceActivationMode.PTT;
	}

	enum VoiceRangeMode
	{
		PROXIMITY("Proximity (nearby players)"),
		FRIENDS_CHAT("Friends Chat (FC members)"),
		BOTH("Both (Proximity + Friends)");

		public final String label;

		VoiceRangeMode(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigItem(
		keyName = "voiceRangeMode",
		name = "Voice Range Mode",
		description = "Choose who can hear your voice"
	)
	default VoiceRangeMode voiceRangeMode()
	{
		return VoiceRangeMode.PROXIMITY;
	}

	@ConfigItem(
		keyName = "pushToTalkKey",
		name = "Push to Talk",
		description = "Hold this key to transmit voice (only used in PTT mode)"
	)
	default Keybind pushToTalkKey()
	{
		return new Keybind(KeyEvent.VK_V, 0);
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "vadThreshold",
		name = "VAD Threshold",
		description = "Microphone level (0-100) that triggers auto-transmit in VAD mode. Higher = less sensitive."
	)
	default int vadThreshold()
	{
		return 5;
	}

	@Range(min = 1, max = 50)
	@ConfigItem(
		keyName = "maxRange",
		name = "Voice Range (tiles)",
		description = "Maximum tile distance to hear other players"
	)
	default int maxRange()
	{
		return 15;
	}

	@Range(min = 0, max = 200)
	@ConfigItem(
		keyName = "inputVolume",
		name = "Microphone Volume %",
		description = "Microphone input gain (100 = normal)"
	)
	default int inputVolume()
	{
		return 150;
	}

	@Range(min = 0, max = 200)
	@ConfigItem(
		keyName = "outputVolume",
		name = "Output Volume %",
		description = "Speaker output gain (100 = normal)"
	)
	default int outputVolume()
	{
		return 150;
	}

	@ConfigItem(
		keyName = "serverUrl",
		name = "Server URL",
		description = "WebSocket relay server URL"
	)
	default String serverUrl()
	{
		return "wss://tilewhisper.horner.codes";
	}

	@ConfigItem(
		keyName = "muteAudio",
		name = "Mute Incoming Audio",
		description = "Mute all incoming voice chat"
	)
	default boolean muteAudio()
	{
		return false;
	}

	@ConfigItem(
		keyName = "friendsOnly",
		name = "Friends Only",
		description = "Only hear and be heard by players on your OSRS friends list"
	)
	default boolean friendsOnly()
	{
		return false;
	}
}
