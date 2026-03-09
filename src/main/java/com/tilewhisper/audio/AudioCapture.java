package com.tilewhisper.audio;

import com.sun.jna.ptr.PointerByReference;
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

	private TargetDataLine targetDataLine;
	private final ExecutorService executorService;
	private volatile boolean running;
	private volatile boolean pushToTalkActive;
	private final Consumer<byte[]> onAudioFrame;
	private final double volumeScale;
	private PointerByReference opusEncoder = null;

	public AudioCapture(Consumer<byte[]> onAudioFrame, int inputVolume)
	{
		this.onAudioFrame = onAudioFrame;
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
		log.info("Audio capture started (Opus codec)");
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

		while (running)
		{
			// Read exactly one PCM frame
			int offset = 0;
			while (offset < FRAME_BYTES_PCM && running)
			{
				int n = targetDataLine.read(pcmBuf, offset, FRAME_BYTES_PCM - offset);
				if (n < 0)
				{
					return;
				}
				offset += n;
			}

			if (!running || !pushToTalkActive)
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
