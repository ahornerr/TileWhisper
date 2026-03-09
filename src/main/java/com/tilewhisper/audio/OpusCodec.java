package com.tilewhisper.audio;

import club.minnced.opus.util.OpusLibrary;
import com.sun.jna.ptr.PointerByReference;
import lombok.extern.slf4j.Slf4j;
import tomp2p.opuswrapper.Opus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

@Slf4j
public class OpusCodec
{
	public static final int SAMPLE_RATE = 16000;
	public static final int CHANNELS = 1;
	// 20ms frame at 16kHz = 320 samples
	public static final int FRAME_SAMPLES = 320;
	public static final int FRAME_BYTES_PCM = FRAME_SAMPLES * 2; // 16-bit = 2 bytes/sample
	public static final int MAX_PACKET_BYTES = 1276; // Opus spec max for a 20ms frame
	public static final int BITRATE = 32000; // 32 kbps — good voice quality

	private static boolean libraryLoaded = false;
	private static boolean loadAttempted = false;

	public static synchronized boolean loadLibrary()
	{
		if (loadAttempted)
		{
			return libraryLoaded;
		}
		loadAttempted = true;
		try
		{
			OpusLibrary.loadFromJar();
			// Force Opus class initialization now so any failure is caught here,
			// not later at createEncoder()/createDecoder() time.
			// On macOS, the tomp2p Opus wrapper uses Native.loadLibrary("") which
			// fails unless the native lib was already loaded via System.load().
			Opus.INSTANCE.getClass();
			libraryLoaded = true;
			log.info("Opus native library loaded successfully");
		}
		catch (Throwable e)
		{
			// Catch Throwable to cover Error subclasses like ExceptionInInitializerError
			// and NoClassDefFoundError that JNA throws on some platforms (e.g. macOS).
			log.warn("Failed to load Opus native library (audio features disabled): {}", e.getMessage());
			libraryLoaded = false;
		}
		return libraryLoaded;
	}

	public static boolean isAvailable()
	{
		return libraryLoaded;
	}

	// --- Encoder ---

	public static PointerByReference createEncoder()
	{
		if (!libraryLoaded)
		{
			throw new IllegalStateException("Opus library not available");
		}
		IntBuffer error = IntBuffer.allocate(1);
		PointerByReference encoder = Opus.INSTANCE.opus_encoder_create(SAMPLE_RATE, CHANNELS, Opus.OPUS_APPLICATION_VOIP, error);
		if (error.get(0) != Opus.OPUS_OK)
		{
			throw new RuntimeException("Failed to create Opus encoder, error code: " + error.get(0));
		}
		Opus.INSTANCE.opus_encoder_ctl(encoder, Opus.OPUS_SET_BITRATE_REQUEST, BITRATE);
		return encoder;
	}

	/**
	 * Encode one frame of PCM to Opus.
	 *
	 * @param encoder pointer from createEncoder()
	 * @param pcm     direct ShortBuffer of FRAME_SAMPLES shorts (LE order)
	 * @param output  direct output ByteBuffer, capacity >= MAX_PACKET_BYTES
	 * @return number of encoded bytes, or <0 on error
	 */
	public static int encode(PointerByReference encoder, ShortBuffer pcm, ByteBuffer output)
	{
		return Opus.INSTANCE.opus_encode(encoder, pcm, FRAME_SAMPLES, output, output.capacity());
	}

	public static void destroyEncoder(PointerByReference encoder)
	{
		Opus.INSTANCE.opus_encoder_destroy(encoder);
	}

	// --- Decoder ---

	public static PointerByReference createDecoder()
	{
		if (!libraryLoaded)
		{
			throw new IllegalStateException("Opus library not available");
		}
		IntBuffer error = IntBuffer.allocate(1);
		PointerByReference decoder = Opus.INSTANCE.opus_decoder_create(SAMPLE_RATE, CHANNELS, error);
		if (error.get(0) != Opus.OPUS_OK)
		{
			throw new RuntimeException("Failed to create Opus decoder, error code: " + error.get(0));
		}
		return decoder;
	}

	/**
	 * Decode an Opus packet to PCM shorts.
	 *
	 * @param decoder  pointer from createDecoder()
	 * @param opusData encoded bytes
	 * @param pcmOut   output ShortBuffer (capacity >= FRAME_SAMPLES)
	 * @return number of decoded samples per channel, or <0 on error
	 */
	public static int decode(PointerByReference decoder, byte[] opusData, ShortBuffer pcmOut)
	{
		return Opus.INSTANCE.opus_decode(decoder, opusData, opusData.length, pcmOut, FRAME_SAMPLES, 0);
	}

	public static void destroyDecoder(PointerByReference decoder)
	{
		Opus.INSTANCE.opus_decoder_destroy(decoder);
	}
}
