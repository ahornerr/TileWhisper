package com.tilewhisper;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Persists the TileWhisper ignore list via a config storage backend.
 * Ignored players produce no audio regardless of proximity or friends status.
 * The list survives plugin restarts.
 */
public class IgnoreListManager
{
	private static final String CONFIG_GROUP = "tilewhisper";
	private static final String CONFIG_KEY = "ignoreList";
	private static final String SEPARATOR = ",";

	private final ConfigStorage storage;
	private final Set<String> ignored = new LinkedHashSet<>();

	public IgnoreListManager(ConfigStorage storage)
	{
		this.storage = storage;
		load();
	}

	public boolean isIgnored(String username)
	{
		return username != null && ignored.contains(username);
	}

	public void ignore(String username)
	{
		if (username == null || username.isEmpty())
		{
			return;
		}
		ignored.add(username);
		save();
	}

	public void unignore(String username)
	{
		ignored.remove(username);
		save();
	}

	public Set<String> getAll()
	{
		return Collections.unmodifiableSet(ignored);
	}

	private void load()
	{
		String raw = storage.get(CONFIG_GROUP, CONFIG_KEY);
		ignored.clear();
		if (raw != null && !raw.isEmpty())
		{
			for (String name : raw.split(SEPARATOR, -1))
			{
				String trimmed = name.trim();
				if (!trimmed.isEmpty())
				{
					ignored.add(trimmed);
				}
			}
		}
	}

	private void save()
	{
		storage.set(CONFIG_GROUP, CONFIG_KEY, String.join(SEPARATOR, ignored));
	}

	/** Interface for config persistence — allows stubbing in tests */
	public interface ConfigStorage
	{
		String get(String group, String key);
		void set(String group, String key, String value);
	}

	/** Adapts RuneLite ConfigManager to ConfigStorage */
	public static class ConfigManagerAdapter implements ConfigStorage
	{
		private final net.runelite.client.config.ConfigManager configManager;

		public ConfigManagerAdapter(net.runelite.client.config.ConfigManager configManager)
		{
			this.configManager = configManager;
		}

		@Override
		public String get(String group, String key)
		{
			return configManager.getConfiguration(group, key);
		}

		@Override
		public void set(String group, String key, String value)
		{
			configManager.setConfiguration(group, key, value);
		}
	}
}
