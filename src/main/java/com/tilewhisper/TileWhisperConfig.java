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
	@ConfigItem(
		keyName = "pushToTalkKey",
		name = "Push to Talk",
		description = "Hold this key to transmit voice"
	)
	default Keybind pushToTalkKey()
	{
		return new Keybind(KeyEvent.VK_V, 0);
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
}
