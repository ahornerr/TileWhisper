package com.tilewhisper;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

import java.util.Arrays;

public class TileWhisperPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TileWhisperPlugin.class);
		// Strip -J prefixed args (bootstrap JVM flags) - not handled by RuneLite.main()
		String[] filteredArgs = Arrays.stream(args)
			.filter(arg -> !arg.startsWith("-J"))
			.toArray(String[]::new);
		RuneLite.main(filteredArgs);
	}
}
