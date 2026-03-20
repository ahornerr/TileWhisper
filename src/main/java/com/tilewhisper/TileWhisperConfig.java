package com.tilewhisper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

import java.awt.event.KeyEvent;

@ConfigGroup("tilewhisper")
public interface TileWhisperConfig extends Config
{
	@ConfigSection(
		name = "Voice Activation",
		description = "How and when your microphone transmits",
		position = 0
	)
	String activationSection = "activation";

	@ConfigSection(
		name = "Range & Filtering",
		description = "Who you hear and how far",
		position = 1
	)
	String rangeSection = "range";

	@ConfigSection(
		name = "Volume",
		description = "Microphone and speaker levels",
		position = 2
	)
	String volumeSection = "volume";

	@ConfigSection(
		name = "Advanced",
		description = "Server connection settings",
		position = 3,
		closedByDefault = true
	)
	String advancedSection = "advanced";

	// --- Voice Activation ---

	enum VoiceActivationMode
	{
		PTT("Push-to-Talk"),
		VAD("Voice Activity (auto)");

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
		name = "Activation Mode",
		description = "PTT: hold a key to talk. VAD: auto-transmit when mic level exceeds threshold.",
		section = activationSection,
		position = 0
	)
	default VoiceActivationMode voiceActivation()
	{
		return VoiceActivationMode.PTT;
	}

	@ConfigItem(
		keyName = "pushToTalkKey",
		name = "PTT Key",
		description = "Hold this key to transmit (PTT mode only)",
		section = activationSection,
		position = 1
	)
	default Keybind pushToTalkKey()
	{
		return new Keybind(KeyEvent.VK_V, 0);
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "vadThreshold",
		name = "VAD Sensitivity",
		description = "Mic level that triggers auto-transmit (VAD mode). Higher = less sensitive.",
		section = activationSection,
		position = 2
	)
	default int vadThreshold()
	{
		return 5;
	}

	// --- Range & Filtering ---

	enum VoiceRangeMode
	{
		PROXIMITY("Proximity"),
		FRIENDS_CHAT("Friends Chat"),
		BOTH("Proximity + Friends");

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
		name = "Range Mode",
		description = "Proximity: nearby players. Friends Chat: FC members. Both: either.",
		section = rangeSection,
		position = 0
	)
	default VoiceRangeMode voiceRangeMode()
	{
		return VoiceRangeMode.PROXIMITY;
	}

	@Range(min = 1, max = 50)
	@ConfigItem(
		keyName = "maxRange",
		name = "Max Range (tiles)",
		description = "Maximum tile distance at which players can be heard",
		section = rangeSection,
		position = 1
	)
	default int maxRange()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "friendsOnly",
		name = "Friends Only",
		description = "Only hear (and be heard by) players on your friends list",
		section = rangeSection,
		position = 2
	)
	default boolean friendsOnly()
	{
		return false;
	}

	@ConfigItem(
		keyName = "muteAudio",
		name = "Mute Incoming Audio",
		description = "Mute all incoming voice chat",
		section = rangeSection,
		position = 3
	)
	default boolean muteAudio()
	{
		return false;
	}

	// --- Volume ---

	@Range(min = 0, max = 200)
	@ConfigItem(
		keyName = "inputVolume",
		name = "Mic Gain (%)",
		description = "Microphone input gain. 100 = normal, 150 = boosted.",
		section = volumeSection,
		position = 0
	)
	default int inputVolume()
	{
		return 150;
	}

	@Range(min = 0, max = 200)
	@ConfigItem(
		keyName = "outputVolume",
		name = "Speaker Volume (%)",
		description = "Output volume for incoming voice. 100 = normal.",
		section = volumeSection,
		position = 1
	)
	default int outputVolume()
	{
		return 150;
	}

	// --- Advanced ---

	@ConfigItem(
		keyName = "serverUrl",
		name = "Server URL",
		description = "WebSocket relay server URL",
		section = advancedSection,
		position = 0
	)
	default String serverUrl()
	{
		return "wss://relay.tilewhisper.com";
	}
}
