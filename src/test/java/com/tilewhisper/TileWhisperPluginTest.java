package com.tilewhisper;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TileWhisperPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TileWhisperPlugin.class);
		RuneLite.main(args);
	}
}
