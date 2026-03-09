package com.tilewhisper.audio;

import com.sun.jna.ptr.PointerByReference;
import com.tilewhisper.TileWhisperConfig.VoiceActivationMode;
import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.*;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Slf4j
public class AudioCapture
{
	// 16kHz, 16-bit, mono, signed, little-endian
	private static final AudioFormat PCM_FORMAT = new AudioFormat(16000, 16, 1, true, false);

	// Opus frame size: 20ms at 16kHz = 320 samples
	public static final int FRAME_SAMPLES = 320;
	public static final int FRAME_BYTES_PCM = FRAME_SAMPLES * 2; // 640 bytes

	// VAD hold time: keep transmitting for this many frames after voice stops (500ms = 25 frames)
	private static final int VAD_HOLD_FRAMES = 25;

	private TargetDataLine targetDataLine;
	private final ExecutorService executorService;
	private volatile boolean running;
	private volatile boolean pushToTalkActive;
	private final Consumer<byte[]> onAudioFrame;
	private final double volumeScale;
	private PointerByReference opusEncoder = null;

	// VAD state
	private final VoiceActivationMode voiceActivationMode;
	private final int vadThreshold;
	private int vadHoldCounter = 0;

	public AudioCapture(Consumer<byte[]> onAudioFrame, int inputVolume,
						VoiceActivationMode voiceActivationMode, int vadThreshold)
	{
		this.onAudioFrame = onAudioFrame;
		this.voiceActivationMode = voiceActivationMode;
		this.vadThreshold = vadThreshold;
		// 100% = 2.0x baseline, 200% = 4.0x amplification
		this.volumeScale = (inputVolume / 100.0) * 4.0;
		this.executorService = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "TileWhisper-AudioCapture");
			t.setDaemon(true);
			return t;
		});
	}

	public void start() throws LineUnavailableException
	{
		if (running)
		{
			return;
		}

		opusEncoder = OpusCodec.createEncoder();
		log.info("Opus encoder created");

		targetDataLine = AudioSystem.getTargetDataLine(PCM_FORMAT);
		targetDataLine.open(PCM_FORMAT);
		targetDataLine.start();

		running = true;
		executorService.submit(this::captureLoop);
		log.info("Audio capture started (Opus codec, {} mode)", voiceActivationMode);
	}

	public void setPushToTalkActive(boolean active)
	{
		this.pushToTalkActive = active;
	}

	public void close()
	{
		running = false;
		executorService.shutdown();
		if (targetDataLine != null)
		{
			targetDataLine.stop();
			targetDataLine.close();
			targetDataLine = null;
		}
		if (opusEncoder != null)
		{
			OpusCodec.destroyEncoder(opusEncoder);
			opusEncoder = null;
		}
		log.info("Audio capture stopped");
	}

	private void captureLoop()
	{
		final byte[] pcmBuf = new byte[FRAME_BYTES_PCM];
		// Direct buffers for Opus encode (JNA requires direct buffers)
		final ByteBuffer opusInputBuf = ByteBuffer.allocateDirect(FRAME_BYTES_PCM)
				.order(java.nio.ByteOrder.LITTLE_ENDIAN);
		final ByteBuffer opusOutputBuf = ByteBuffer.allocateDirect(OpusCodec.MAX_PACKET_BYTES);

		int audioUnavailableRetryCount = 0;

		while (running)
		{
			// Read exactly one PCM frame
			int offset = 0;
			while (offset < FRAME_BYTES_PCM && running)
			{
				try
				{
					int n = targetDataLine.read(pcmBuf, offset, FRAME_BYTES_PCM - offset);
					if (n < 0)
					{
						return;
					}
					offset += n;
					audioUnavailableRetryCount = 0; // Reset retry count on successful read
				}
				catch (Exception e)
				{
					log.warn("Microphone read error: {}", e.getMessage());
					audioUnavailableRetryCount++;

					if (audioUnavailableRetryCount <= 3)
					{
						try
						{
							Thread.sleep(500);
							targetDataLine.stop();
							targetDataLine.close();
							targetDataLine = AudioSystem.getTargetDataLine(PCM_FORMAT);
							targetDataLine.open(PCM_FORMAT);
							targetDataLine.start();
							log.info("Microphone reconnected after attempt {}", audioUnavailableRetryCount);
							audioUnavailableRetryCount = 0;
						}
						catch (Exception re)
						{
							log.error("Failed to reconnect microphone attempt {}", audioUnavailableRetryCount, re);
						}
					}
					else
					{
						log.error("Microphone reconnection failed after 3 attempts, audio capture disabled");
						return;
					}
				}
			}

			// Determine if we should transmit based on activation mode
			boolean shouldTransmit;
			if (voiceActivationMode == VoiceActivationMode.PTT)
			{
				shouldTransmit = pushToTalkActive;
			}
			else // VAD mode
			{
				// vadThreshold is 0-100; map to 0-32767 RMS range (scale * 327 ~= 0.1% of max)
				int rmsThreshold = vadThreshold * 327;
				int audioLevel = calculateRMS(pcmBuf);
				boolean voiceDetected = audioLevel > rmsThreshold;

				if (voiceDetected)
				{
					vadHoldCounter = VAD_HOLD_FRAMES;
					shouldTransmit = true;
				}
				else if (vadHoldCounter > 0)
				{
					vadHoldCounter--;
					shouldTransmit = true;
				}
				else
				{
					shouldTransmit = false;
				}
			}

			if (!running || !shouldTransmit)
			{
				continue;
			}

			// Apply volume scaling
			byte[] scaledPcm = applyVolumePcm(pcmBuf, volumeScale);

			// Encode PCM -> Opus
			opusInputBuf.clear();
			opusInputBuf.put(scaledPcm);
			opusInputBuf.flip();

			opusOutputBuf.clear();
			int encodedBytes = OpusCodec.encode(opusEncoder, opusInputBuf.asShortBuffer(), opusOutputBuf);

			if (encodedBytes > 0)
			{
				byte[] packet = new byte[encodedBytes];
				opusOutputBuf.rewind();
				opusOutputBuf.get(packet);
				onAudioFrame.accept(packet);
			}
		}
	}

	/**
	 * Calculate the RMS (Root Mean Square) of the PCM data.
	 * Returns a value from 0-32767 (16-bit signed range).
	 */
	private static int calculateRMS(byte[] pcm)
	{
		long sum = 0;
		for (int i = 0; i + 1 < pcm.length; i += 2)
		{
			int sample = (short) ((pcm[i + 1] << 8) | (pcm[i] & 0xFF));
			sum += (long) sample * sample;
		}
		double mean = sum / (pcm.length / 2.0);
		return (int) Math.sqrt(mean);
	}

	private static byte[] applyVolumePcm(byte[] pcm, double scale)
	{
		if (scale == 1.0)
		{
			return pcm;
		}
		byte[] out = new byte[pcm.length];
		for (int i = 0; i + 1 < pcm.length; i += 2)
		{
			int sample = (short) ((pcm[i + 1] << 8) | (pcm[i] & 0xFF));
			sample = (int) (sample * scale);
			if (sample > 32767) sample = 32767;
			if (sample < -32768) sample = -32768;
			out[i] = (byte) (sample & 0xFF);
			out[i + 1] = (byte) ((sample >> 8) & 0xFF);
		}
		return out;
	}
}
