package com.tilewhisper.network;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Data
@AllArgsConstructor
public class VoicePacket
{
	private int world;
	private int x;
	private int y;
	private int plane;
	private String username;
	private byte[] audioData;

	public byte[] toBytes()
	{
		byte[] usernameBytes = username.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		int usernameLen = Math.min(usernameBytes.length, 12); // OSRS username max 12 chars

		ByteBuffer buffer = ByteBuffer.allocate(14 + usernameLen + audioData.length);
		buffer.order(ByteOrder.LITTLE_ENDIAN);

		buffer.putInt(world);
		buffer.putInt(x);
		buffer.putInt(y);
		buffer.put((byte) plane);
		buffer.put((byte) usernameLen);
		buffer.put(usernameBytes, 0, usernameLen);
		buffer.put(audioData);

		return buffer.array();
	}

	public static VoicePacket fromBytes(byte[] data)
	{
		if (data.length < 14)
		{
			throw new IllegalArgumentException("Packet too short: " + data.length);
		}

		ByteBuffer buffer = ByteBuffer.wrap(data);
		buffer.order(ByteOrder.LITTLE_ENDIAN);

		int world = buffer.getInt();
		int x = buffer.getInt();
		int y = buffer.getInt();
		int plane = buffer.get() & 0xFF;
		int usernameLen = buffer.get() & 0xFF;

		if (data.length < 14 + usernameLen)
		{
			throw new IllegalArgumentException("Packet too short for username: " + data.length);
		}

		byte[] usernameBytes = new byte[usernameLen];
		buffer.get(usernameBytes);
		String username = new String(usernameBytes, java.nio.charset.StandardCharsets.UTF_8);

		byte[] audioData = new byte[data.length - 14 - usernameLen];
		buffer.get(audioData);

		return new VoicePacket(world, x, y, plane, username, audioData);
	}
}
