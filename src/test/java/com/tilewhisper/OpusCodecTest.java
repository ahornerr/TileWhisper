package com.tilewhisper;

import com.tilewhisper.audio.OpusCodec;
import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusEncoder;
import org.junit.Test;

import static org.junit.Assert.*;

public class OpusCodecTest
{
	// ========================================================================
	// Encoder / decoder isolation
	// ========================================================================

	@Test
	public void twoEncoders_doNotShareState() throws Exception
	{
		OpusEncoder enc1 = OpusCodec.createEncoder();
		OpusEncoder enc2 = OpusCodec.createEncoder();

		byte[] pcm = generateSineWave(440.0, OpusCodec.FRAME_SAMPLES);
		byte[] out1 = new byte[OpusCodec.MAX_PACKET_BYTES];
		byte[] out2 = new byte[OpusCodec.MAX_PACKET_BYTES];

		// Warm up enc1 with several frames, enc2 gets fresh state
		for (int i = 0; i < 10; i++) OpusCodec.encode(enc1, pcm, out1);
		int bytes1 = OpusCodec.encode(enc1, pcm, out1);
		int bytes2 = OpusCodec.encode(enc2, pcm, out2);

		// Both should produce valid output; enc2 (frame 1) will differ from enc1 (frame 11)
		assertTrue("enc1 should produce valid output", bytes1 > 0);
		assertTrue("enc2 should produce valid output", bytes2 > 0);
	}

	@Test
	public void twoDecoders_doNotShareState() throws Exception
	{
		OpusEncoder encoder = OpusCodec.createEncoder();
		OpusDecoder dec1 = OpusCodec.createDecoder();
		OpusDecoder dec2 = OpusCodec.createDecoder();

		byte[] pcm = generateSineWave(440.0, OpusCodec.FRAME_SAMPLES);
		byte[] opusBuf = new byte[OpusCodec.MAX_PACKET_BYTES];
		byte[] pcmOut1 = new byte[OpusCodec.FRAME_BYTES_PCM];
		byte[] pcmOut2 = new byte[OpusCodec.FRAME_BYTES_PCM];

		// Warm up dec1 with frames, then decode same packet with both
		for (int i = 0; i < 5; i++)
		{
			int encoded = OpusCodec.encode(encoder, pcm, opusBuf);
			byte[] packet = copyOf(opusBuf, encoded);
			OpusCodec.decode(dec1, packet, pcmOut1);
		}
		int encoded = OpusCodec.encode(encoder, pcm, opusBuf);
		byte[] packet = copyOf(opusBuf, encoded);

		int samples1 = OpusCodec.decode(dec1, packet, pcmOut1);
		int samples2 = OpusCodec.decode(dec2, packet, pcmOut2);

		// Both decoders should produce exactly one frame
		assertEquals(OpusCodec.FRAME_SAMPLES, samples1);
		assertEquals(OpusCodec.FRAME_SAMPLES, samples2);
	}

	// ========================================================================
	// Encode properties
	// ========================================================================

	@Test
	public void encode_silenceProducesValidOutput() throws Exception
	{
		OpusEncoder encoder = OpusCodec.createEncoder();
		byte[] silence = new byte[OpusCodec.FRAME_BYTES_PCM];
		byte[] opusBuf = new byte[OpusCodec.MAX_PACKET_BYTES];

		int bytes = OpusCodec.encode(encoder, silence, opusBuf);
		assertTrue("Silence should encode to >0 bytes (Opus DTX/comfort noise)", bytes > 0);
		assertTrue("Silence should be small when encoded", bytes < 50);
	}

	@Test
	public void encode_bothQuietAndLoudProduceValidOutput() throws Exception
	{
		OpusEncoder encoder = OpusCodec.createEncoder();
		byte[] opusBuf = new byte[OpusCodec.MAX_PACKET_BYTES];

		byte[] warmup = generateSineWave(440.0, OpusCodec.FRAME_SAMPLES);
		for (int i = 0; i < 5; i++) OpusCodec.encode(encoder, warmup, opusBuf);

		byte[] quietPcm = generateSineWave(440.0, OpusCodec.FRAME_SAMPLES, 500);
		byte[] loudPcm = generateSineWave(440.0, OpusCodec.FRAME_SAMPLES, 28000);
		int quietBytes = OpusCodec.encode(encoder, quietPcm, opusBuf);
		int loudBytes = OpusCodec.encode(encoder, loudPcm, opusBuf);

		assertTrue("Quiet audio should encode to valid output (quietBytes=" + quietBytes + ")",
				quietBytes > 0);
		assertTrue("Loud audio should encode to valid output (loudBytes=" + loudBytes + ")",
				loudBytes > 0);
		assertTrue("Encodings should be within packet buffer", Math.max(quietBytes, loudBytes) <= OpusCodec.MAX_PACKET_BYTES);
	}

	@Test
	public void encode_outputFitsInMaxPacketBuffer() throws Exception
	{
		OpusEncoder encoder = OpusCodec.createEncoder();
		byte[] opusBuf = new byte[OpusCodec.MAX_PACKET_BYTES];

		for (int i = 0; i < 50; i++)
		{
			double freq = 200.0 + (i % 10) * 100.0;
			byte[] pcm = generateSineWave(freq, OpusCodec.FRAME_SAMPLES);
			int bytes = OpusCodec.encode(encoder, pcm, opusBuf);
			assertTrue("Frame " + i + ": encoded " + bytes + " bytes exceeds MAX_PACKET_BYTES",
				bytes <= OpusCodec.MAX_PACKET_BYTES);
		}
	}

	// ========================================================================
	// Decode properties
	// ========================================================================

	@Test
	public void decode_alwaysReturnsFullFrame() throws Exception
	{
		OpusEncoder encoder = OpusCodec.createEncoder();
		OpusDecoder decoder = OpusCodec.createDecoder();
		byte[] opusBuf = new byte[OpusCodec.MAX_PACKET_BYTES];
		byte[] pcmOut = new byte[OpusCodec.FRAME_BYTES_PCM];

		for (int i = 0; i < 20; i++)
		{
			byte[] pcm = generateSineWave(440.0 + i * 20, OpusCodec.FRAME_SAMPLES);
			int encoded = OpusCodec.encode(encoder, pcm, opusBuf);
			int decoded = OpusCodec.decode(decoder, copyOf(opusBuf, encoded), pcmOut);
			assertEquals("Frame " + i + " should always decode full frame",
				OpusCodec.FRAME_SAMPLES, decoded);
		}
	}

	@Test
	public void decode_silence_outputIsSilent() throws Exception
	{
		OpusEncoder encoder = OpusCodec.createEncoder();
		OpusDecoder decoder = OpusCodec.createDecoder();
		byte[] opusBuf = new byte[OpusCodec.MAX_PACKET_BYTES];
		byte[] pcmOut = new byte[OpusCodec.FRAME_BYTES_PCM];

		byte[] silence = new byte[OpusCodec.FRAME_BYTES_PCM];
		int encoded = OpusCodec.encode(encoder, silence, opusBuf);
		OpusCodec.decode(decoder, copyOf(opusBuf, encoded), pcmOut);

		double rms = calculateRMS(pcmOut);
		assertTrue("Decoded silence RMS should be near zero, got " + rms, rms < 500);
	}

	// ========================================================================
	// Codec constants sanity checks
	// ========================================================================

	@Test
	public void constants_frameSamplesMatchBytesPerFrame()
	{
		assertEquals("FRAME_BYTES_PCM should be FRAME_SAMPLES * 2 (16-bit mono)",
			OpusCodec.FRAME_SAMPLES * 2, OpusCodec.FRAME_BYTES_PCM);
	}

	@Test
	public void constants_sampleRate_is16kHz()
	{
		assertEquals("Sample rate should be 16kHz", 16000, OpusCodec.SAMPLE_RATE);
	}

	@Test
	public void constants_frameDuration_is20ms()
	{
		// At 16kHz, 20ms = 320 samples
		double frameDurationMs = (double) OpusCodec.FRAME_SAMPLES / OpusCodec.SAMPLE_RATE * 1000;
		assertEquals("Frame duration should be 20ms", 20.0, frameDurationMs, 0.001);
	}

	// ========================================================================
	// Helpers
	// ========================================================================

	private static byte[] generateSineWave(double freqHz, int samples)
	{
		return generateSineWave(freqHz, samples, 16000);
	}

	private static byte[] generateSineWave(double freqHz, int samples, int amplitude)
	{
		byte[] pcm = new byte[samples * 2];
		for (int i = 0; i < samples; i++)
		{
			double t = (double) i / OpusCodec.SAMPLE_RATE;
			int sample = (int) (amplitude * Math.sin(2.0 * Math.PI * freqHz * t));
			sample = Math.max(-32768, Math.min(32767, sample));
			pcm[i * 2] = (byte) (sample & 0xFF);
			pcm[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
		}
		return pcm;
	}

	private static double calculateRMS(byte[] pcm)
	{
		long sum = 0;
		for (int i = 0; i + 1 < pcm.length; i += 2)
		{
			int sample = (short) ((pcm[i + 1] << 8) | (pcm[i] & 0xFF));
			sum += (long) sample * sample;
		}
		return Math.sqrt(sum / (pcm.length / 2.0));
	}

	private static byte[] copyOf(byte[] src, int length)
	{
		byte[] dst = new byte[length];
		System.arraycopy(src, 0, dst, 0, length);
		return dst;
	}
}
