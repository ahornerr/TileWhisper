package com.tilewhisper;

import com.tilewhisper.network.VoicePacket;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.Assert.*;

public class VoicePacketTest
{
	// ========================================================================
	// Roundtrip edge cases
	// ========================================================================

	@Test
	public void roundtrip_emptyAudio()
	{
		VoicePacket packet = new VoicePacket(301, 3200, 3200, 0, "Player", new byte[0]);
		VoicePacket result = VoicePacket.fromBytes(packet.toBytes());

		assertEquals("Player", result.getUsername());
		assertArrayEquals(new byte[0], result.getAudioData());
	}

	@Test
	public void roundtrip_minimalValues()
	{
		VoicePacket packet = new VoicePacket(0, 0, 0, 0, "A", new byte[]{42});
		VoicePacket result = VoicePacket.fromBytes(packet.toBytes());

		assertEquals(0, result.getWorld());
		assertEquals(0, result.getX());
		assertEquals(0, result.getY());
		assertEquals(0, result.getPlane());
		assertEquals("A", result.getUsername());
		assertArrayEquals(new byte[]{42}, result.getAudioData());
	}

	@Test
	public void roundtrip_largeAudioPayload()
	{
		byte[] audio = new byte[1276]; // Max Opus packet size
		for (int i = 0; i < audio.length; i++) audio[i] = (byte) (i & 0xFF);

		VoicePacket packet = new VoicePacket(301, 3200, 3200, 0, "Player", audio);
		VoicePacket result = VoicePacket.fromBytes(packet.toBytes());

		assertArrayEquals(audio, result.getAudioData());
	}

	@Test
	public void roundtrip_allPlanesPreserved()
	{
		for (int plane = 0; plane <= 3; plane++)
		{
			VoicePacket packet = new VoicePacket(301, 3200, 3200, plane, "P", new byte[]{1});
			VoicePacket result = VoicePacket.fromBytes(packet.toBytes());
			assertEquals("Plane " + plane + " should round-trip", plane, result.getPlane());
		}
	}

	@Test
	public void roundtrip_allBytesInAudioPreserved()
	{
		// Verify all 256 byte values survive serialization (no sign extension, no masking)
		byte[] audio = new byte[256];
		for (int i = 0; i < 256; i++) audio[i] = (byte) i;

		VoicePacket packet = new VoicePacket(301, 3200, 3200, 0, "Player", audio);
		VoicePacket result = VoicePacket.fromBytes(packet.toBytes());

		assertArrayEquals(audio, result.getAudioData());
	}

	// ========================================================================
	// Username truncation
	// ========================================================================

	@Test
	public void username_exactly12Chars_notTruncated()
	{
		String name = "TwelveCharsX"; // 12 chars
		VoicePacket packet = new VoicePacket(301, 3200, 3200, 0, name, new byte[]{1});
		VoicePacket result = VoicePacket.fromBytes(packet.toBytes());
		assertEquals(name, result.getUsername());
	}

	@Test
	public void username_over12Chars_truncatedTo12()
	{
		String longName = "ThisNameIsWayTooLong"; // > 12 chars
		VoicePacket packet = new VoicePacket(301, 3200, 3200, 0, longName, new byte[]{1});
		VoicePacket result = VoicePacket.fromBytes(packet.toBytes());
		assertEquals("ThisNameIsWa", result.getUsername()); // first 12 chars
	}

	@Test
	public void username_singleChar_preserved()
	{
		VoicePacket packet = new VoicePacket(301, 3200, 3200, 0, "X", new byte[]{1});
		VoicePacket result = VoicePacket.fromBytes(packet.toBytes());
		assertEquals("X", result.getUsername());
	}

	// ========================================================================
	// Byte layout / endianness
	// ========================================================================

	@Test
	public void byteLayout_fieldsAtCorrectOffsets()
	{
		// Verify: [world:4][x:4][y:4][plane:1][usernameLen:1][username:N][audio:M]
		VoicePacket packet = new VoicePacket(999, 1111, 2222, 2, "AB", new byte[]{7, 8, 9});
		byte[] bytes = packet.toBytes();

		ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		assertEquals(999, buf.getInt());   // world at offset 0
		assertEquals(1111, buf.getInt());  // x at offset 4
		assertEquals(2222, buf.getInt());  // y at offset 8
		assertEquals(2, buf.get() & 0xFF); // plane at offset 12
		assertEquals(2, buf.get() & 0xFF); // usernameLen at offset 13
	}

	@Test
	public void packetSize_correctForGivenInputs()
	{
		// Header is always 14 bytes + usernameLen + audioLen
		byte[] audio = new byte[50];
		String username = "TestUser"; // 8 chars
		VoicePacket packet = new VoicePacket(301, 3200, 3200, 0, username, audio);
		assertEquals(14 + 8 + 50, packet.toBytes().length);
	}

	@Test
	public void worldField_largePosValue_preserved()
	{
		VoicePacket packet = new VoicePacket(Integer.MAX_VALUE, 0, 0, 0, "P", new byte[]{1});
		VoicePacket result = VoicePacket.fromBytes(packet.toBytes());
		assertEquals(Integer.MAX_VALUE, result.getWorld());
	}
}
