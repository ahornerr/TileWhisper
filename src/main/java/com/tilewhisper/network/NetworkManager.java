package com.tilewhisper.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Slf4j
public class NetworkManager
{
	private static final int MAX_RECONNECT_ATTEMPTS = 10;
	private static final long[] RECONNECT_DELAYS_MS = {5000, 10000, 20000, 30000, 60000};

	private final Gson gson = new Gson();
	private WebSocket webSocket;
	private final AtomicBoolean connected = new AtomicBoolean(false);
	private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "TileWhisper-Scheduler");
		t.setDaemon(true);
		return t;
	});

	private final Consumer<List<NearbyPlayer>> onNearbyPlayers;
	private final BiConsumer<VoicePacket, byte[]> onAudioReceived;
	private final Consumer<Boolean> onConnectionChanged;

	private String currentUrl;
	private volatile HttpClient httpClient;
	// 3 permits allows pipelining audio sends without dropping frames on typical latency
	private final java.util.concurrent.Semaphore audioSendPermit = new java.util.concurrent.Semaphore(3);

	public NetworkManager(
		Consumer<List<NearbyPlayer>> onNearbyPlayers,
		BiConsumer<VoicePacket, byte[]> onAudioReceived,
		Consumer<Boolean> onConnectionChanged)
	{
		this.onNearbyPlayers = onNearbyPlayers;
		this.onAudioReceived = onAudioReceived;
		this.onConnectionChanged = onConnectionChanged;
		this.httpClient = HttpClient.newHttpClient();
	}

	public void connect(String url)
	{
		this.currentUrl = url;
		reconnectAttempts.set(0);
		doConnect();
	}

	private void doConnect()
	{
		if (httpClient == null)
		{
			log.warn("Cannot connect: HttpClient is null (already closed?)");
			return;
		}

		try
		{
			URI uri = URI.create(currentUrl);
			log.info("Connecting to TileWhisper server: {}", uri);

			// Non-blocking connect — avoid freezing the calling thread
			httpClient.newWebSocketBuilder()
				.buildAsync(uri, new WebSocketListener())
				.whenComplete((ws, ex) -> {
					if (ex != null)
					{
						log.error("Failed to connect to server", ex);
						scheduleReconnect();
					}
					else
					{
						webSocket = ws;
					}
				});
		}
		catch (Exception e)
		{
			log.error("Failed to initiate connection to server", e);
			scheduleReconnect();
		}
	}

	public void disconnect()
	{
		connected.set(false);
		if (webSocket != null)
		{
			webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Disconnecting").exceptionally(ex -> {
				log.warn("Error during close", ex);
				return null;
			});
			webSocket = null;
		}
	}

	public void sendPresence(int world, int x, int y, int plane, String username)
	{
		if (!connected.get() || webSocket == null)
		{
			return;
		}

		JsonObject json = new JsonObject();
		json.addProperty("type", "presence");
		json.addProperty("world", world);
		json.addProperty("x", x);
		json.addProperty("y", y);
		json.addProperty("plane", plane);
		json.addProperty("username", username);

		String message = gson.toJson(json);
		webSocket.sendText(message, true).exceptionally(ex -> {
			log.error("Failed to send presence", ex);
			return null;
		});
	}

	public void sendAudio(int world, int x, int y, int plane, String username, byte[] encoded)
	{
		if (!connected.get() || webSocket == null)
		{
			return;
		}

		// Non-blocking tryAcquire: drop frame only if 3+ sends are already in flight
		if (!audioSendPermit.tryAcquire())
		{
			log.debug("Dropping audio frame: {} sends pending", 3 - audioSendPermit.availablePermits());
			return;
		}

		try
		{
			VoicePacket packet = new VoicePacket(world, x, y, plane, username, encoded);
			byte[] bytes = packet.toBytes();

			log.debug("Sending audio: {} bytes from {}", bytes.length, username);
			CompletableFuture<WebSocket> sendFuture = webSocket.sendBinary(ByteBuffer.wrap(bytes), true);

			// whenComplete fires for ALL outcomes: success, failure, cancellation
			sendFuture.whenComplete((result, ex) -> {
				audioSendPermit.release();
				if (ex != null)
				{
					log.warn("Audio send failed: {}", ex.getMessage());
				}
			});
		}
		catch (Throwable e)
		{
			log.error("Unexpected error during sendAudio", e);
			audioSendPermit.release(); // Ensure permit is released even on unexpected errors
		}
	}

	public boolean isConnected()
	{
		return connected.get();
	}

	public void close()
	{
		disconnect();
		scheduler.shutdownNow();
		httpClient = null;
	}

	private void scheduleReconnect()
	{
		int attempt = reconnectAttempts.incrementAndGet();
		if (attempt > MAX_RECONNECT_ATTEMPTS)
		{
			log.warn("Max reconnect attempts reached, giving up");
			return;
		}

		int delayIndex = Math.min(attempt - 1, RECONNECT_DELAYS_MS.length - 1);
		long delayMs = RECONNECT_DELAYS_MS[delayIndex];

		log.info("Reconnecting in {}ms (attempt {}/{})", delayMs, attempt, MAX_RECONNECT_ATTEMPTS);
		scheduler.schedule(this::doConnect, delayMs, TimeUnit.MILLISECONDS);
	}

	private class WebSocketListener implements WebSocket.Listener
	{
		private final StringBuilder textAccumulator = new StringBuilder();
		private final java.io.ByteArrayOutputStream binaryAccumulator = new java.io.ByteArrayOutputStream();

		@Override
		public void onOpen(WebSocket webSocket)
		{
			log.info("WebSocket connection opened");
			connected.set(true);
			reconnectAttempts.set(0);
			onConnectionChanged.accept(true);
			webSocket.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last)
		{
			textAccumulator.append(data);

			if (last)
			{
				String message = textAccumulator.toString();
				textAccumulator.setLength(0);

				try
				{
					JsonObject json = new JsonParser().parse(message).getAsJsonObject();
					String type = json.get("type").getAsString();

					if ("nearby".equals(type))
					{
						List<NearbyPlayer> players = gson.fromJson(
							json.get("players"),
							new TypeToken<List<NearbyPlayer>>() {}.getType()
						);
						onNearbyPlayers.accept(players);
					}
					else if ("welcome".equals(type))
					{
						log.info("Received welcome from server");
					}
				}
				catch (Exception e)
				{
					log.error("Failed to parse text message: {}", message, e);
				}
			}

			webSocket.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last)
		{
			byte[] bytes = new byte[data.remaining()];
			data.get(bytes);
			binaryAccumulator.write(bytes, 0, bytes.length);

			if (last)
			{
				byte[] fullData = binaryAccumulator.toByteArray();
				binaryAccumulator.reset();

				try
				{
					VoicePacket packet = VoicePacket.fromBytes(fullData);
					onAudioReceived.accept(packet, packet.getAudioData());
				}
				catch (Exception e)
				{
					log.error("Failed to parse binary audio packet", e);
				}
			}

			webSocket.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason)
		{
			log.info("WebSocket closed: {} {}", statusCode, reason);
			connected.set(false);
			onConnectionChanged.accept(false);

			if (statusCode != WebSocket.NORMAL_CLOSURE)
			{
				scheduleReconnect();
			}

			return null;
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error)
		{
			log.error("WebSocket error", error);
			connected.set(false);
			onConnectionChanged.accept(false);
			scheduleReconnect();
		}
	}
}
