package com.tilewhisper.audio;

import com.sun.jna.ptr.PointerByReference;
import lombok.extern.slf4j.Slf4j;

import javax.sound.sampled.*;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
public class AudioPlayback
{
	private static final AudioFormat PCM_FORMAT = new AudioFormat(16000, 16, 1, true, false);

	private static final class AudioFrame
	{
		final String username;
		final byte[] audioData;
		final float volumeFactor;

		AudioFrame(String username, byte[] audioData, float volumeFactor)
		{
			this.username = username;
			this.audioData = audioData;
			this.volumeFactor = volumeFactor;
		}
	}

	private SourceDataLine sourceDataLine;
	private final float outputVolumeScale;
	private final byte[] pcmBuf = new byte[AudioCapture.FRAME_BYTES_PCM];
	private final Map<String, PointerByReference> decoders = new ConcurrentHashMap<>();
	private final Map<String, Float> playerVolumes = new ConcurrentHashMap<>();
	private final Map<String, Boolean> playerMuted = new ConcurrentHashMap<>();

	// Direct buffer for Opus decode output
	private final ByteBuffer opusDecodeDirectBuf;
	private final ShortBuffer opusDecodeShortBuf;

	// Dedicated playback thread with bounded queue
	private final BlockingQueue<AudioFrame> frameQueue = new LinkedBlockingQueue<>(50);
	private final ExecutorService playbackExecutor;

	public AudioPlayback(int outputVolume)
	{
		this.outputVolumeScale = outputVolume / 100.0f;
		this.opusDecodeDirectBuf = ByteBuffer.allocateDirect(AudioCapture.FRAME_BYTES_PCM)
				.order(java.nio.ByteOrder.LITTLE_ENDIAN);
		this.opusDecodeShortBuf = opusDecodeDirectBuf.asShortBuffer();
		this.playbackExecutor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "TileWhisper-AudioPlayback");
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
		log.info("Audio playback started (Opus codec)");

		playbackExecutor.submit(this::playbackLoop);
	}

	private void playbackLoop()
	{
		while (!Thread.currentThread().isInterrupted())
		{
			try
			{
				AudioFrame frame = frameQueue.poll(100, TimeUnit.MILLISECONDS);
				if (frame == null) continue;
				writeFrame(frame);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	/** Enqueue audio for playback — returns immediately, does not block. */
	public void playAudio(String username, byte[] audioData, float volumeFactor)
	{
		if (sourceDataLine == null || audioData.length == 0 || volumeFactor <= 0)
		{
			return;
		}

		// Check if player is muted
		if (Boolean.TRUE.equals(playerMuted.get(username)))
		{
			return;
		}

		if (!frameQueue.offer(new AudioFrame(username, audioData, volumeFactor)))
		{
			log.warn("Audio queue full, dropping frame from {}", username);
		}
	}

	private void writeFrame(AudioFrame frame)
	{
		// Get player's custom volume (default 1.0)
		float playerVolume = playerVolumes.getOrDefault(frame.username, 1.0f);

		// 100% config = 2.0x baseline, 200% = 4.0x
		float scale = frame.volumeFactor * playerVolume * Math.max(0, outputVolumeScale) * 4.0f;

		try
		{
			PointerByReference decoder = decoders.computeIfAbsent(frame.username, u -> {
				log.info("Creating Opus decoder for {}", u);
				return OpusCodec.createDecoder();
			});

			opusDecodeShortBuf.clear();
			int decodedSamples = OpusCodec.decode(decoder, frame.audioData, opusDecodeShortBuf);

			if (decodedSamples > 0)
			{
				int pcmBytes = decodedSamples * 2;
				opusDecodeDirectBuf.rewind();
				opusDecodeDirectBuf.get(pcmBuf, 0, pcmBytes);
				applyVolumePcm(pcmBuf, pcmBytes, scale);
				sourceDataLine.write(pcmBuf, 0, pcmBytes);
			}
			else
			{
				log.warn("Opus decode error {} for {}", decodedSamples, frame.username);
			}
		}
		catch (Exception e)
		{
			log.error("Error writing audio frame from {}", frame.username, e);
			PointerByReference decoder = decoders.remove(frame.username);
			if (decoder != null) OpusCodec.destroyDecoder(decoder);
		}
	}

	private static void applyVolumePcm(byte[] pcm, int length, float scale)
	{
		for (int i = 0; i + 1 < length; i += 2)
		{
			int sample = (short) ((pcm[i + 1] << 8) | (pcm[i] & 0xFF));
			sample = (int) (sample * scale);
			if (sample > 32767) sample = 32767;
			if (sample < -32768) sample = -32768;
			pcm[i] = (byte) (sample & 0xFF);
			pcm[i + 1] = (byte) ((sample >> 8) & 0xFF);
		}
	}

	/**
	 * Set volume multiplier for a specific player (0.0 to 2.0).
	 * 1.0 = normal, 0.0 = muted (use setPlayerMuted instead for explicit mute)
	 */
	public void setPlayerVolume(String username, float volumeMultiplier)
	{
		playerVolumes.put(username, Math.max(0.0f, Math.min(2.0f, volumeMultiplier)));
		log.debug("Set volume for {} to {}", username, volumeMultiplier);
	}

	/**
	 * Get current volume multiplier for a player.
	 */
	public float getPlayerVolume(String username)
	{
		return playerVolumes.getOrDefault(username, 1.0f);
	}

	/**
	 * Mute or unmute a specific player.
	 */
	public void setPlayerMuted(String username, boolean muted)
	{
		playerMuted.put(username, muted);
		log.debug("Player {} muted: {}", username, muted);
	}

	/**
	 * Check if a player is muted.
	 */
	public boolean isPlayerMuted(String username)
	{
		return Boolean.TRUE.equals(playerMuted.get(username));
	}

	/**
	 * Clear per-player state when a player leaves proximity.
	 */
	public void cleanupPlayer(String username)
	{
		PointerByReference decoder = decoders.remove(username);
		if (decoder != null) OpusCodec.destroyDecoder(decoder);
		playerVolumes.remove(username);
		playerMuted.remove(username);
		log.debug("Cleaned up state for {}", username);
	}

	public void close()
	{
		playbackExecutor.shutdownNow();
		if (sourceDataLine != null)
		{
			sourceDataLine.drain();
			sourceDataLine.close();
			sourceDataLine = null;
			log.info("Audio playback stopped");
		}
		for (PointerByReference decoder : decoders.values())
		{
			OpusCodec.destroyDecoder(decoder);
		}
		decoders.clear();
		playerVolumes.clear();
		playerMuted.clear();
	}

	// Cleanup decoder only (for when player leaves range but may return)
	public void cleanupPlayerDecoder(String username)
	{
		PointerByReference decoder = decoders.remove(username);
		if (decoder != null) OpusCodec.destroyDecoder(decoder);
	}
}
