package com.tilewhisper.network;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NearbyPlayer
{
	private String username;
	private int world;
	private int x;
	private int y;
	private int plane;
}
