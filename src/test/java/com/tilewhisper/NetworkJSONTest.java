package com.tilewhisper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.tilewhisper.network.NearbyPlayer;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class NetworkJSONTest
{
	private final Gson gson = new Gson();

	// ========================================================================
	// NearbyPlayer JSON parsing
	// ========================================================================

	@Test
	public void nearbyPlayer_singlePlayer_parsesCorrectly()
	{
		String json = "{\"username\":\"Player1\",\"world\":301,\"x\":3200,\"y\":3200,\"plane\":0}";
		NearbyPlayer player = gson.fromJson(json, NearbyPlayer.class);

		assertEquals("Player1", player.getUsername());
		assertEquals(301, player.getWorld());
		assertEquals(3200, player.getX());
		assertEquals(3200, player.getY());
		assertEquals(0, player.getPlane());
	}

	@Test
	public void nearbyPlayer_multiplePlayers_parsesAll()
	{
		String json = "{\"type\":\"nearby\",\"players\":["
			+ "{\"username\":\"PlayerA\",\"world\":302,\"x\":3210,\"y\":3210,\"plane\":1},"
			+ "{\"username\":\"PlayerB\",\"world\":302,\"x\":3211,\"y\":3211,\"plane\":1}"
			+ "]}";

		JsonElement root = gson.fromJson(json, JsonElement.class);
		JsonObject rootObj = root.getAsJsonObject();
		JsonArray playersArray = rootObj.getAsJsonArray("players");

		List<NearbyPlayer> players = gson.fromJson(playersArray, new TypeToken<List<NearbyPlayer>>() {}.getType());

		assertEquals(2, players.size());
		assertEquals("PlayerA", players.get(0).getUsername());
		assertEquals("PlayerB", players.get(1).getUsername());
	}

	@Test
	public void nearbyPlayer_allZeros_parsesCorrectly()
	{
		String json = "{\"username\":\"Zero\",\"world\":0,\"x\":0,\"y\":0,\"plane\":0}";
		NearbyPlayer player = gson.fromJson(json, NearbyPlayer.class);

		assertEquals("Zero", player.getUsername());
		assertEquals(0, player.getWorld());
		assertEquals(0, player.getX());
		assertEquals(0, player.getY());
		assertEquals(0, player.getPlane());
	}

	@Test
	public void nearbyPlayer_negativeValues_parsesCorrectly()
	{
		String json = "{\"username\":\"Minus\",\"world\":-1,\"x\":-1000,\"y\":-2000,\"plane\":-1}";
		NearbyPlayer player = gson.fromJson(json, NearbyPlayer.class);

		assertEquals("Minus", player.getUsername());
		assertEquals(-1, player.getWorld());
		assertEquals(-1000, player.getX());
		assertEquals(-2000, player.getY());
		assertEquals(-1, player.getPlane());
	}

	@Test
	public void nearbyPlayer_largeValues_parsesCorrectly()
	{
		String json = "{\"username\":\"Far\",\"world\":999,\"x\":16383,\"y\":16383,\"plane\":3}";
		NearbyPlayer player = gson.fromJson(json, NearbyPlayer.class);

		assertEquals("Far", player.getUsername());
		assertEquals(999, player.getWorld());
		assertEquals(16383, player.getX());
		assertEquals(16383, player.getY());
		assertEquals(3, player.getPlane());
	}

	@Test
	public void nearbyPlayer_emptyPlayersArray_returnsEmptyList()
	{
		String json = "{\"type\":\"nearby\",\"players\":[]}";
		JsonElement root = gson.fromJson(json, JsonElement.class);
		JsonObject rootObj = root.getAsJsonObject();
		JsonArray playersArray = rootObj.getAsJsonArray("players");

		List<NearbyPlayer> players = gson.fromJson(playersArray, new TypeToken<List<NearbyPlayer>>() {}.getType());

		assertTrue("Empty players array should return empty list", players.isEmpty());
	}

	// ========================================================================
	// WebSocket message types
	// ========================================================================

	@Test
	public void message_type_welcome_isRecognized()
	{
		String json = "{\"type\":\"welcome\"}";
		JsonElement root = gson.fromJson(json, JsonElement.class);
		assertEquals("welcome", root.getAsJsonObject().get("type").getAsString());
	}

	@Test
	public void message_type_presence_isRecognized()
	{
		String json = "{\"type\":\"presence\",\"world\":301,\"x\":3200,\"y\":3200,\"plane\":0,\"username\":\"Player1\"}";
		JsonElement root = gson.fromJson(json, JsonElement.class);
		JsonObject rootObj = root.getAsJsonObject();

		assertEquals("presence", rootObj.get("type").getAsString());
		assertEquals(301, rootObj.get("world").getAsInt());
		assertEquals("Player1", rootObj.get("username").getAsString());
	}

	@Test
	public void message_type_nearby_isRecognized()
	{
		String json = "{\"type\":\"nearby\",\"players\":[{\"username\":\"P\",\"world\":301,\"x\":3200,\"y\":3200,\"plane\":0}]}";
		JsonElement root = gson.fromJson(json, JsonElement.class);
		JsonObject rootObj = root.getAsJsonObject();

		assertEquals("nearby", rootObj.get("type").getAsString());
		assertTrue("Should have players array", rootObj.has("players"));
		assertTrue("Players should be an array", rootObj.get("players").isJsonArray());
	}

	// ========================================================================
	// Presence message round-trip (serialization)
	// ========================================================================

	@Test
	public void presence_serialization_allFieldsIncluded()
	{
		JsonObject json = new JsonObject();
		json.addProperty("type", "presence");
		json.addProperty("world", 301);
		json.addProperty("x", 3200);
		json.addProperty("y", 3200);
		json.addProperty("plane", 0);
		json.addProperty("username", "TestPlayer");

		String serialized = gson.toJson(json);
		JsonObject parsed = gson.fromJson(serialized, JsonObject.class);

		assertEquals("presence", parsed.get("type").getAsString());
		assertEquals(301, parsed.get("world").getAsInt());
		assertEquals("TestPlayer", parsed.get("username").getAsString());
	}
}
