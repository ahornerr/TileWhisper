package com.tilewhisper;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class FriendsFilteringTest
{
	// ========================================================================
	// Friends-only mode filtering
	// ========================================================================

	@Test
	public void friendsOnly_mode_withFriend_returnsTrue()
	{
		Set<String> friends = new HashSet<>(Arrays.asList("Alice", "Bob"));
		boolean result = TileWhisperPlugin.shouldReceiveAudio(
			"Bob", friends, true, TileWhisperConfig.VoiceRangeMode.PROXIMITY
		);
		assertTrue("Friend should receive audio in friends-only mode", result);
	}

	@Test
	public void friendsOnly_mode_withoutFriend_returnsFalse()
	{
		Set<String> friends = new HashSet<>(Arrays.asList("Alice", "Bob"));
		boolean result = TileWhisperPlugin.shouldReceiveAudio(
			"Charlie", friends, true, TileWhisperConfig.VoiceRangeMode.PROXIMITY
		);
		assertFalse("Non-friend should not receive audio in friends-only mode", result);
	}

	@Test
	public void friendsOnly_mode_withEmptyFriends_returnsFalse()
	{
		Set<String> friends = Collections.emptySet();
		boolean result = TileWhisperPlugin.shouldReceiveAudio(
			"Anyone", friends, true, TileWhisperConfig.VoiceRangeMode.PROXIMITY
		);
		assertFalse("No one should receive audio with empty friends list in friends-only mode", result);
	}

	// ========================================================================
	// VoiceRangeMode: FRIENDS_CHAT
	// ========================================================================

	@Test
	public void voiceRangeMode_friendsChat_withFriend_returnsTrue()
	{
		Set<String> friends = new HashSet<>(Arrays.asList("Alice", "Bob"));
		boolean result = TileWhisperPlugin.shouldReceiveAudio(
			"Alice", friends, false, TileWhisperConfig.VoiceRangeMode.FRIENDS_CHAT
		);
		assertTrue("FC friend should receive audio in FRIENDS_CHAT mode", result);
	}

	@Test
	public void voiceRangeMode_friendsChat_withoutFriend_returnsFalse()
	{
		Set<String> friends = new HashSet<>(Arrays.asList("Alice", "Bob"));
		boolean result = TileWhisperPlugin.shouldReceiveAudio(
			"Charlie", friends, false, TileWhisperConfig.VoiceRangeMode.FRIENDS_CHAT
		);
		assertFalse("Non-FC member should not receive audio in FRIENDS_CHAT mode", result);
	}

	@Test
	public void voiceRangeMode_friendsChat_withFriendsOnly_andFriend_returnsTrue()
	{
		Set<String> friends = new HashSet<>(Arrays.asList("Alice"));
		boolean result = TileWhisperPlugin.shouldReceiveAudio(
			"Alice", friends, true, TileWhisperConfig.VoiceRangeMode.FRIENDS_CHAT
		);
		assertTrue("FC friend should receive audio with friends-only enabled", result);
	}

	@Test
	public void voiceRangeMode_friendsChat_withFriendsOnly_withoutFriend_returnsFalse()
	{
		Set<String> friends = new HashSet<>(Arrays.asList("Alice"));
		boolean result = TileWhisperPlugin.shouldReceiveAudio(
			"Bob", friends, true, TileWhisperConfig.VoiceRangeMode.FRIENDS_CHAT
		);
		assertFalse("Non-FC member should not receive audio with friends-only enabled", result);
	}

	// ========================================================================
	// VoiceRangeMode: BOTH (proximity + friends)
	// ========================================================================

	@Test
	public void voiceRangeMode_both_withFriend_returnsTrue()
	{
		Set<String> friends = new HashSet<>(Arrays.asList("Alice"));
		boolean result = TileWhisperPlugin.shouldReceiveAudio(
			"Alice", friends, false, TileWhisperConfig.VoiceRangeMode.BOTH
		);
		assertTrue("Friend should receive audio in BOTH mode", result);
	}

	@Test
	public void voiceRangeMode_both_withoutFriend_returnsTrue()
	{
		Set<String> friends = new HashSet<>(Arrays.asList("Alice"));
		boolean result = TileWhisperPlugin.shouldReceiveAudio(
			"Bob", friends, false, TileWhisperConfig.VoiceRangeMode.BOTH
		);
		assertTrue("Non-friend should receive audio in BOTH mode (proximity applies)", result);
	}

	@Test
	public void voiceRangeMode_both_withFriendsOnly_withFriend_returnsTrue()
	{
		Set<String> friends = new HashSet<>(Arrays.asList("Alice"));
		boolean result = TileWhisperPlugin.shouldReceiveAudio(
			"Alice", friends, true, TileWhisperConfig.VoiceRangeMode.BOTH
		);
		assertTrue("Friend should receive audio in BOTH mode with friends-only enabled", result);
	}

	@Test
	public void voiceRangeMode_both_withFriendsOnly_withoutFriend_returnsFalse()
	{
		Set<String> friends = new HashSet<>(Arrays.asList("Alice"));
		boolean result = TileWhisperPlugin.shouldReceiveAudio(
			"Bob", friends, true, TileWhisperConfig.VoiceRangeMode.BOTH
		);
		assertFalse("Non-friend should not receive audio in BOTH mode with friends-only enabled", result);
	}

	// ========================================================================
	// VoiceRangeMode: PROXIMITY (default)
	// ========================================================================

	@Test
	public void voiceRangeMode_proximity_withFriend_returnsTrue()
	{
		Set<String> friends = new HashSet<>(Arrays.asList("Alice"));
		boolean result = TileWhisperPlugin.shouldReceiveAudio(
			"Alice", friends, false, TileWhisperConfig.VoiceRangeMode.PROXIMITY
		);
		assertTrue("Friend should receive audio in PROXIMITY mode", result);
	}

	@Test
	public void voiceRangeMode_proximity_withoutFriend_returnsTrue()
	{
		Set<String> friends = new HashSet<>(Arrays.asList("Alice"));
		boolean result = TileWhisperPlugin.shouldReceiveAudio(
			"Bob", friends, false, TileWhisperConfig.VoiceRangeMode.PROXIMITY
		);
		assertTrue("Non-friend should receive audio in PROXIMITY mode", result);
	}

	@Test
	public void voiceRangeMode_proximity_withFriendsOnly_withFriend_returnsTrue()
	{
		Set<String> friends = new HashSet<>(Arrays.asList("Alice"));
		boolean result = TileWhisperPlugin.shouldReceiveAudio(
			"Alice", friends, true, TileWhisperConfig.VoiceRangeMode.PROXIMITY
		);
		assertTrue("Friend should receive audio in PROXIMITY mode with friends-only enabled", result);
	}

	@Test
	public void voiceRangeMode_proximity_withFriendsOnly_withoutFriend_returnsFalse()
	{
		Set<String> friends = new HashSet<>(Arrays.asList("Alice"));
		boolean result = TileWhisperPlugin.shouldReceiveAudio(
			"Bob", friends, true, TileWhisperConfig.VoiceRangeMode.PROXIMITY
		);
		assertFalse("Non-friend should not receive audio in PROXIMITY mode with friends-only enabled", result);
	}

	// ========================================================================
	// Edge cases
	// ========================================================================

	@Test
	public void edgeCase_nullUsername_returnsFalse()
	{
		Set<String> friends = new HashSet<>(Arrays.asList("Alice"));
		boolean result = TileWhisperPlugin.shouldReceiveAudio(
			null, friends, false, TileWhisperConfig.VoiceRangeMode.PROXIMITY
		);
		assertFalse("Null username should not receive audio", result);
	}

	@Test
	public void edgeCase_nullFriends_returnsFalse()
	{
		boolean result = TileWhisperPlugin.shouldReceiveAudio(
			"Alice", null, false, TileWhisperConfig.VoiceRangeMode.PROXIMITY
		);
		assertFalse("Null friends set should not receive audio", result);
	}

	@Test
	public void edgeCase_caseInsensitiveUsername_notHandled()
	{
		// "Alice" is a friend but "alice" is not (case-sensitive Set lookup).
		// friendsOnly=true so the friends check is exercised.
		Set<String> friends = new HashSet<>(Arrays.asList("Alice"));
		boolean result = TileWhisperPlugin.shouldReceiveAudio(
			"alice", friends, true, TileWhisperConfig.VoiceRangeMode.PROXIMITY
		);
		assertFalse("Username matching is case-sensitive: 'alice' != 'Alice'", result);
	}

	@Test
	public void edgeCase_multipleFriends_allMatch()
	{
		Set<String> friends = new HashSet<>(Arrays.asList("Alice", "Bob", "Charlie"));
		assertTrue("Alice should match", TileWhisperPlugin.shouldReceiveAudio(
			"Alice", friends, false, TileWhisperConfig.VoiceRangeMode.PROXIMITY
		));
		assertTrue("Bob should match", TileWhisperPlugin.shouldReceiveAudio(
			"Bob", friends, false, TileWhisperConfig.VoiceRangeMode.PROXIMITY
		));
		assertTrue("Charlie should match", TileWhisperPlugin.shouldReceiveAudio(
			"Charlie", friends, false, TileWhisperConfig.VoiceRangeMode.PROXIMITY
		));
	}
}
