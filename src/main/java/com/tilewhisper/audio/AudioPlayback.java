package com.tilewhisper.audio;

import lombok.extern.slf4j.Slf4j;
import io.github.jaredmdobson.concentus.OpusDecoder;

import javax.sound.sampled.*;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
public class AudioPlayback
{
	private static final AudioFormat PCM_FORMAT = new AudioFormat(16000, 16, 1, true, false);

	/** Encoded audio frame with distance-based volume factor. */
	private static final class PendingFrame
	{
		final byte[] audioData;
		final float volumeFactor;

		PendingFrame(byte[] audioData, float volumeFactor)
		{
			this.audioData = audioData;
			this.volumeFactor = volumeFactor;
		}
	}

	private SourceDataLine sourceDataLine;
	private volatile float outputVolumeScale;

	// Per-player state
	private final Map<String, OpusDecoder> decoders = new ConcurrentHashMap<>();
	private final Map<String, Float> playerVolumes = new ConcurrentHashMap<>();
	private final Map<String, Boolean> playerMuted = new ConcurrentHashMap<>();
	// Per-player pending frame queues (bounded to prevent memory pressure from bursts)
	private final Map<String, ConcurrentLinkedQueue<PendingFrame>> pendingFrames = new ConcurrentHashMap<>();
	private static final int MAX_PENDING_FRAMES_PER_PLAYER = 10; // 200ms of audio

	// Mixer buffers — only accessed from the scheduler thread
	private final short[] decodeBuffer = new short[AudioCapture.FRAME_SAMPLES];
	private final int[] mixBuffer = new int[AudioCapture.FRAME_SAMPLES];
	private final byte[] outputBuffer = new byte[AudioCapture.FRAME_BYTES_PCM];

	// Audio limiter applied to the MIXED output (not per-player)
	private float limiterEnvelope = 0.0f;
	private static final float LIMITER_ATTACK = 0.995f;  // Fast attack
	private static final float LIMITER_RELEASE = 0.005f; // Slow release
	private static final float LIMITER_THRESHOLD = 24000.0f;  // Threshold (~0.73 of max)
	private static final float MAX_ABSOLUTE = 32000.0f;  // Hard cap (~0.98 of max)

	// Scheduled mixer fires every 20ms, matching the Opus frame period
	private final ScheduledExecutorService mixerScheduler;

	public AudioPlayback(int outputVolume)
	{
		this.outputVolumeScale = outputVolume / 100.0f;
		this.mixerScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "TileWhisper-AudioMixer");
			t.setDaemon(true);
			return t;
		});
	}

	public void start() throws LineUnavailableException
	{
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, PCM_FORMAT);
		sourceDataLine = (SourceDataLine) AudioSystem.getLine(info);
		sourceDataLine.open(PCM_FORMAT);

		if (sourceDataLine.isControlSupported(FloatControl.Type.MASTER_GAIN))
		{
			FloatControl gain = (FloatControl) sourceDataLine.getControl(FloatControl.Type.MASTER_GAIN);
			gain.setValue(gain.getMaximum());
			log.info("Master gain set to {}dB (max)", gain.getMaximum());
		}

		sourceDataLine.start();

		// Schedule mixer at 20ms fixed rate (one Opus frame period)
		mixerScheduler.scheduleAtFixedRate(this::mixAndWriteFrame, 0, 20, TimeUnit.MILLISECONDS);
		log.info("Audio playback started (Opus codec, mixed output)");
	}

	/** Enqueue audio for playback — returns immediately, does not block. */
	public void playAudio(String username, byte[] audioData, float volumeFactor)
	{
		if (sourceDataLine == null || audioData.length == 0 || volumeFactor <= 0)
		{
			return;
		}

		if (Boolean.TRUE.equals(playerMuted.get(username)))
		{
			return;
		}

		ConcurrentLinkedQueue<PendingFrame> queue =
			pendingFrames.computeIfAbsent(username, u -> new ConcurrentLinkedQueue<>());

		// Drop oldest frame if queue is full (burst protection)
		if (queue.size() >= MAX_PENDING_FRAMES_PER_PLAYER)
		{
			log.warn("Audio queue full for {}, dropping oldest frame", username);
			queue.poll();
		}

		queue.offer(new PendingFrame(audioData, volumeFactor));
	}

	/**
	 * Mixer: fires every 20ms. Pops one frame per active player, decodes them all,
	 * sums the PCM samples into a mix buffer, applies the limiter, and writes the
	 * combined output to the source data line. All speakers are heard simultaneously.
	 */
	private void mixAndWriteFrame()
	{
		Arrays.fill(mixBuffer, 0);
		boolean hasAudio = false;

		for (Map.Entry<String, ConcurrentLinkedQueue<PendingFrame>> entry : pendingFrames.entrySet())
		{
			String username = entry.getKey();

			if (Boolean.TRUE.equals(playerMuted.get(username)))
			{
				entry.getValue().clear(); // Discard queued frames for muted players
				continue;
			}

			PendingFrame frame = entry.getValue().poll();
			if (frame == null)
			{
				continue; // No frame from this player this tick
			}

			try
			{
				OpusDecoder decoder = decoders.computeIfAbsent(username, u -> {
					log.info("Creating Opus decoder for {}", u);
					try
					{
						return OpusCodec.createDecoder();
					}
					catch (Exception e)
					{
						throw new RuntimeException("Failed to create decoder for " + u, e);
					}
				});

				int decodedSamples = OpusCodec.decodeToShorts(decoder, frame.audioData, decodeBuffer);
				if (decodedSamples > 0)
				{
					float playerVolume = playerVolumes.getOrDefault(username, 1.0f);
					// Per-player scale: distance factor * per-player knob
					// Global output volume applied later to the full mix
					float perPlayerScale = frame.volumeFactor * playerVolume;
					for (int i = 0; i < decodedSamples; i++)
					{
						mixBuffer[i] += (int) (decodeBuffer[i] * perPlayerScale);
					}
					hasAudio = true;
				}
				else
				{
					log.warn("Opus decode error {} for {}", decodedSamples, username);
					decoders.remove(username); // Force decoder recreation next time
				}
			}
			catch (Exception e)
			{
				log.error("Error decoding audio frame from {}", username, e);
				decoders.remove(username); // Force decoder recreation next time
			}
		}

		if (!hasAudio || sourceDataLine == null)
		{
			return;
		}

		// Apply global output scale (100% = 2x baseline, 200% = 4x)
		float globalScale = Math.max(0, outputVolumeScale) * 4.0f;

		// Convert mixed int buffer to bytes, apply global scale, and limit
		for (int i = 0; i < AudioCapture.FRAME_SAMPLES; i++)
		{
			int sample = (int) (mixBuffer[i] * globalScale);

			// Apply limiter
			float absSample = Math.abs(sample);
			if (absSample > limiterEnvelope)
			{
				limiterEnvelope += (absSample - limiterEnvelope) * LIMITER_ATTACK;
			}
			else
			{
				limiterEnvelope -= (limiterEnvelope - absSample) * LIMITER_RELEASE;
			}

			float gain = Math.min(1.0f, LIMITER_THRESHOLD / Math.max(limiterEnvelope, LIMITER_THRESHOLD));
			sample = (int) (sample * gain);

			if (sample > MAX_ABSOLUTE) sample = (int) MAX_ABSOLUTE;
			else if (sample < -MAX_ABSOLUTE) sample = -(int) MAX_ABSOLUTE;

			outputBuffer[i * 2] = (byte) (sample & 0xFF);
			outputBuffer[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
		}

		sourceDataLine.write(outputBuffer, 0, outputBuffer.length);
	}

	public void setOutputVolume(int outputVolume)
	{
		this.outputVolumeScale = outputVolume / 100.0f;
	}

	public void setPlayerVolume(String username, float volumeMultiplier)
	{
		playerVolumes.put(username, Math.max(0.0f, Math.min(2.0f, volumeMultiplier)));
	}

	public float getPlayerVolume(String username)
	{
		return playerVolumes.getOrDefault(username, 1.0f);
	}

	public void setPlayerMuted(String username, boolean muted)
	{
		playerMuted.put(username, muted);
	}

	public boolean isPlayerMuted(String username)
	{
		return Boolean.TRUE.equals(playerMuted.get(username));
	}

	public void cleanupPlayer(String username)
	{
		decoders.remove(username);
		playerVolumes.remove(username);
		playerMuted.remove(username);
		pendingFrames.remove(username);
	}

	public void close()
	{
		mixerScheduler.shutdownNow();
		try
		{
			if (!mixerScheduler.awaitTermination(1, TimeUnit.SECONDS))
			{
				mixerScheduler.shutdownNow();
			}
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}

		if (sourceDataLine != null)
		{
			sourceDataLine.flush(); // Use flush instead of drain to avoid blocking shutdown
			sourceDataLine.stop();
			sourceDataLine.close();
			sourceDataLine = null;
			log.info("Audio playback stopped");
		}
		decoders.clear();
		playerVolumes.clear();
		playerMuted.clear();
		pendingFrames.clear();
	}
}
