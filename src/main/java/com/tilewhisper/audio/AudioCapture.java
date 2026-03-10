package com.tilewhisper.audio;

import com.tilewhisper.TileWhisperConfig.VoiceActivationMode;
import lombok.extern.slf4j.Slf4j;
import io.github.jaredmdobson.concentus.OpusEncoder;

import javax.sound.sampled.*;
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
	private volatile double volumeScale;
	private final short[] pcmShorts = new short[FRAME_SAMPLES];
	private OpusEncoder opusEncoder = null;

	// VAD state
	private volatile VoiceActivationMode voiceActivationMode;
	private volatile int vadThreshold;
	private int vadHoldCounter = 0;
	private int frameCounter = 0;

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

		try
		{
			opusEncoder = OpusCodec.createEncoder();
			log.info("Opus encoder created");
		}
		catch (Exception e)
		{
			throw new LineUnavailableException("Failed to create Opus encoder: " + e.getMessage());
		}

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

	public void setVoiceActivation(VoiceActivationMode mode, int threshold)
	{
		this.voiceActivationMode = mode;
		this.vadThreshold = threshold;
		log.info("Voice activation: {}, VAD threshold: {} (RMS threshold: {})", mode, threshold, threshold * 327);
	}

	public void setInputVolume(int inputVolume)
	{
		this.volumeScale = (inputVolume / 100.0) * 4.0;
		log.debug("Input volume scale updated: {}x", this.volumeScale);
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
		opusEncoder = null; // Concentus encoder is GC'd
		log.info("Audio capture stopped");
	}

	private void captureLoop()
	{
		final byte[] pcmBuf = new byte[FRAME_BYTES_PCM];
		final byte[] opusOutputBuf = new byte[OpusCodec.MAX_PACKET_BYTES];
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
					audioUnavailableRetryCount = 0;
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

			// Determine if we should transmit
			boolean shouldTransmit;
			if (voiceActivationMode == VoiceActivationMode.PTT)
			{
				shouldTransmit = pushToTalkActive;
			}
			else // VAD mode
			{
				int rmsThreshold = vadThreshold * 327;
				int audioLevel = calculateRMS(pcmBuf);
				boolean voiceDetected = audioLevel > rmsThreshold;

				// Debug: log VAD levels occasionally (every 10 frames = 200ms)
				if (running && ++frameCounter % 10 == 0)
				{
					log.debug("VAD: level={}, threshold={}, hold={}, transmitting={}",
							audioLevel, rmsThreshold, vadHoldCounter, voiceDetected || vadHoldCounter > 0);
				}

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

			// Apply volume scaling, converting to shorts for encoder
			applyVolumePcmToShorts(pcmBuf, pcmShorts, volumeScale);

			// Encode PCM -> Opus
			try
			{
				int encodedBytes = OpusCodec.encode(opusEncoder, pcmShorts, opusOutputBuf);
				if (encodedBytes > 0)
				{
					byte[] packet = new byte[encodedBytes];
					System.arraycopy(opusOutputBuf, 0, packet, 0, encodedBytes);
					onAudioFrame.accept(packet);
				}
			}
			catch (Exception e)
			{
				log.warn("Opus encode error: {}", e.getMessage());
			}
		}
	}

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

	private static void applyVolumePcmToShorts(byte[] pcm, short[] out, double scale)
	{
		for (int i = 0; i < out.length; i++)
		{
			int sample = (short) ((pcm[i * 2 + 1] << 8) | (pcm[i * 2] & 0xFF));
			sample = (int) (sample * scale);
			if (sample > 32767) sample = 32767;
			if (sample < -32768) sample = -32768;
			out[i] = (short) sample;
		}
	}
}
