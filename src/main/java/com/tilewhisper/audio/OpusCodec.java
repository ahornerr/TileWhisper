package com.tilewhisper.audio;

import lombok.extern.slf4j.Slf4j;
import io.github.jaredmdobson.concentus.*;

@Slf4j
public class OpusCodec
{
	public static final int SAMPLE_RATE = 16000;
	public static final int CHANNELS = 1;
	public static final int FRAME_SAMPLES = 320; // 20ms at 16kHz
	public static final int FRAME_BYTES_PCM = FRAME_SAMPLES * 2;
	public static final int MAX_PACKET_BYTES = 1276;
	public static final int BITRATE = 32000; // 32 kbps

	// Concentus is pure Java — no native loading needed.
	public static synchronized boolean loadLibrary()
	{
		return true;
	}

	public static boolean isAvailable()
	{
		return true;
	}

	public static OpusEncoder createEncoder() throws OpusException
	{
		OpusEncoder encoder = new OpusEncoder(SAMPLE_RATE, CHANNELS, OpusApplication.OPUS_APPLICATION_VOIP);
		encoder.setBitrate(BITRATE);
		return encoder;
	}

	public static int encode(OpusEncoder encoder, short[] pcm, byte[] opusOutput) throws OpusException
	{
		return encoder.encode(pcm, 0, FRAME_SAMPLES, opusOutput, 0, opusOutput.length);
	}

	// Backward-compatible overload for tests
	public static int encode(OpusEncoder encoder, byte[] pcmBytes, byte[] opusOutput) throws OpusException
	{
		short[] pcm = new short[FRAME_SAMPLES];
		bytesToShorts(pcmBytes, pcm);
		return encoder.encode(pcm, 0, FRAME_SAMPLES, opusOutput, 0, opusOutput.length);
	}

	public static void destroyEncoder(OpusEncoder encoder)
	{
		// Nothing to do — GC handles it
	}

	public static int decodeToShorts(OpusDecoder decoder, byte[] opusData, short[] pcmOutput) throws OpusException
	{
		int samples = decoder.decode(opusData, 0, opusData.length, pcmOutput, 0, FRAME_SAMPLES, false);
		return samples;
	}

	public static OpusDecoder createDecoder() throws OpusException
	{
		return new OpusDecoder(SAMPLE_RATE, CHANNELS);
	}

	public static int decode(OpusDecoder decoder, byte[] opusData, byte[] pcmOutput) throws OpusException
	{
		short[] pcmShorts = new short[FRAME_SAMPLES];
		int samples = decoder.decode(opusData, 0, opusData.length, pcmShorts, 0, FRAME_SAMPLES, false);
		if (samples > 0)
		{
			shortsToBytes(pcmShorts, pcmOutput, samples);
		}
		return samples;
	}

	public static void destroyDecoder(OpusDecoder decoder)
	{
		// Nothing to do — GC handles it
	}

	private static void bytesToShorts(byte[] bytes, short[] shorts)
	{
		for (int i = 0; i < shorts.length; i++)
		{
			shorts[i] = (short) ((bytes[i * 2 + 1] << 8) | (bytes[i * 2] & 0xFF));
		}
	}

	private static void shortsToBytes(short[] shorts, byte[] bytes, int count)
	{
		for (int i = 0; i < count; i++)
		{
			bytes[i * 2] = (byte) (shorts[i] & 0xFF);
			bytes[i * 2 + 1] = (byte) ((shorts[i] >> 8) & 0xFF);
		}
	}
}
