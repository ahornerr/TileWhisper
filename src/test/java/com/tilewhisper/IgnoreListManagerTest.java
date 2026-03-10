package com.tilewhisper;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class IgnoreListManagerTest
{
	/** Simple in-memory ConfigStorage stub — no RuneLite dependency. */
	private static class MapStorage implements IgnoreListManager.ConfigStorage
	{
		private final Map<String, String> store = new HashMap<>();

		@Override
		public String get(String group, String key)
		{
			return store.get(group + "." + key);
		}

		@Override
		public void set(String group, String key, String value)
		{
			store.put(group + "." + key, value);
		}
	}

	private MapStorage storage;
	private IgnoreListManager ignoreList;

	@Before
	public void setUp()
	{
		storage = new MapStorage();
		ignoreList = new IgnoreListManager(storage);
	}

	// ========================================================================
	// Basic add / remove
	// ========================================================================

	@Test
	public void ignore_addsPlayer()
	{
		ignoreList.ignore("Alice");
		assertTrue("Alice should be ignored after ignore()", ignoreList.isIgnored("Alice"));
	}

	@Test
	public void unignore_removesPlayer()
	{
		ignoreList.ignore("Bob");
		ignoreList.unignore("Bob");
		assertFalse("Bob should not be ignored after unignore()", ignoreList.isIgnored("Bob"));
	}

	@Test
	public void unignore_nonExistentPlayer_noError()
	{
		ignoreList.unignore("Nobody");
		assertFalse(ignoreList.isIgnored("Nobody"));
	}

	@Test
	public void ignore_duplicateAdd_doesNotGrowList()
	{
		ignoreList.ignore("Alice");
		ignoreList.ignore("Alice");
		assertTrue(ignoreList.isIgnored("Alice"));
		assertEquals("Duplicate add should not grow the list", 1, ignoreList.getAll().size());
	}

	@Test
	public void isIgnored_unknownPlayer_returnsFalse()
	{
		assertFalse(ignoreList.isIgnored("Stranger"));
	}

	@Test
	public void isIgnored_null_returnsFalse()
	{
		assertFalse("isIgnored(null) should return false safely", ignoreList.isIgnored(null));
	}

	// ========================================================================
	// Persistence (save / load round-trip)
	// ========================================================================

	@Test
	public void persistence_singlePlayer_survivesReload()
	{
		ignoreList.ignore("Alice");
		IgnoreListManager reloaded = new IgnoreListManager(storage);
		assertTrue("Alice should still be ignored after reload", reloaded.isIgnored("Alice"));
	}

	@Test
	public void persistence_multiplePlayers_allSurviveReload()
	{
		ignoreList.ignore("Alice");
		ignoreList.ignore("Bob");
		ignoreList.ignore("Charlie");

		IgnoreListManager reloaded = new IgnoreListManager(storage);
		assertTrue(reloaded.isIgnored("Alice"));
		assertTrue(reloaded.isIgnored("Bob"));
		assertTrue(reloaded.isIgnored("Charlie"));
		assertEquals(3, reloaded.getAll().size());
	}

	@Test
	public void persistence_unignore_persistsRemoval()
	{
		ignoreList.ignore("Alice");
		ignoreList.ignore("Bob");
		ignoreList.unignore("Alice");

		IgnoreListManager reloaded = new IgnoreListManager(storage);
		assertFalse("Alice should not be ignored after unignore + reload", reloaded.isIgnored("Alice"));
		assertTrue("Bob should still be ignored", reloaded.isIgnored("Bob"));
	}

	@Test
	public void persistence_emptyList_reloadsClean()
	{
		IgnoreListManager reloaded = new IgnoreListManager(storage);
		assertTrue("Empty ignore list should reload as empty", reloaded.getAll().isEmpty());
	}

	// ========================================================================
	// Edge cases
	// ========================================================================

	@Test
	public void ignore_nullUsername_doesNotAdd()
	{
		ignoreList.ignore(null);
		assertFalse(ignoreList.isIgnored(null));
		assertEquals(0, ignoreList.getAll().size());
	}

	@Test
	public void ignore_emptyUsername_doesNotAdd()
	{
		ignoreList.ignore("");
		assertFalse(ignoreList.isIgnored(""));
		assertEquals(0, ignoreList.getAll().size());
	}

	@Test
	public void getAll_returnsUnmodifiableView()
	{
		ignoreList.ignore("Alice");
		try
		{
			ignoreList.getAll().add("Hacker");
			fail("Expected UnsupportedOperationException from unmodifiable view");
		}
		catch (UnsupportedOperationException expected)
		{
			// correct
		}
	}

	@Test
	public void isIgnored_caseSensitive()
	{
		ignoreList.ignore("Alice");
		assertFalse("Case must match exactly: 'alice' != 'Alice'", ignoreList.isIgnored("alice"));
		assertFalse("Case must match exactly: 'ALICE' != 'Alice'", ignoreList.isIgnored("ALICE"));
	}

	@Test
	public void multipleIgnores_getAll_correctSize()
	{
		ignoreList.ignore("A");
		ignoreList.ignore("B");
		ignoreList.ignore("C");
		assertEquals(3, ignoreList.getAll().size());

		ignoreList.unignore("B");
		assertEquals(2, ignoreList.getAll().size());
	}

	@Test
	public void ignore_whitespaceAroundName_stripped()
	{
		// Simulate a saved value with extra spaces (e.g., from a hand-edited config)
		storage.set("tilewhisper", "ignoreList", " Alice , Bob ");
		IgnoreListManager reloaded = new IgnoreListManager(storage);
		assertTrue("Whitespace-padded name should be trimmed on load", reloaded.isIgnored("Alice"));
		assertTrue(reloaded.isIgnored("Bob"));
	}
}
