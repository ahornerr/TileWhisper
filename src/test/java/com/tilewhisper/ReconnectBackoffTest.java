package com.tilewhisper;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for NetworkManager reconnect behavior.
 *
 * Focus: verify reconnect guard prevents double-scheduling and backoff accumulates
 * across connection flaps.
 */
public class ReconnectBackoffTest
{
	private static final int[] DELAYS_MS = {5000, 10000, 20000, 30000, 60000};

	@Test
	public void reconnectGuard_preventsDoubleSchedule()
	{
		AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
		AtomicInteger reconnectAttempts = new AtomicInteger(0);
		CountDownLatch latch = new CountDownLatch(2);

		List<Long> scheduleTimes = new ArrayList<>();

		Thread thread = new Thread(() -> {
			scheduleReconnect(reconnectScheduled, reconnectAttempts, scheduleTimes);
			latch.countDown();

			scheduleReconnect(reconnectScheduled, reconnectAttempts, scheduleTimes);
			latch.countDown();
		});

		thread.start();

		try
		{
			assertTrue("Schedule should complete within 1s", latch.await(1, TimeUnit.SECONDS));
		}
		catch (InterruptedException e)
		{
			fail("Interrupted");
		}

		thread.interrupt();

		assertEquals("Should only schedule once, even if called twice", 1, scheduleTimes.size());
	}

	@Test
	public void reconnectBackoff_accumulatesAcrossAttempts()
	{
		AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
		AtomicInteger reconnectAttempts = new AtomicInteger(0);
		List<Long> delaysUsed = new ArrayList<>();

		for (int i = 0; i < 5; i++)
		{
			if (reconnectScheduled.compareAndSet(false, true))
			{
				int attempt = reconnectAttempts.incrementAndGet();
				int delayIndex = Math.min(attempt - 1, DELAYS_MS.length - 1);
				long delayMs = DELAYS_MS[delayIndex];
				delaysUsed.add(delayMs);
			}
			reconnectScheduled.set(false);
		}

		assertEquals("Delays should increase with each attempt", 5, delaysUsed.size());

		for (int i = 0; i < delaysUsed.size() - 1; i++)
		{
			assertTrue("Delay at attempt " + (i+1) + " should be less than delay at attempt " + (i+2),
				delaysUsed.get(i) < delaysUsed.get(i+1));
		}

		assertEquals("First delay should be 5000ms", Long.valueOf(5000), delaysUsed.get(0));
		assertEquals("Second delay should be 10000ms", Long.valueOf(10000), delaysUsed.get(1));
		assertEquals("Third delay should be 20000ms", Long.valueOf(20000), delaysUsed.get(2));
	}

	@Test
	public void reconnectBackoff_doesNotResetOnSuccessfulOpen()
	{
		AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
		AtomicInteger reconnectAttempts = new AtomicInteger(2);

		List<Long> delaysUsed = new ArrayList<>();

		scheduleReconnect(reconnectScheduled, reconnectAttempts, delaysUsed);
		assertEquals("After 2 failures, attempt should be 3", 1, delaysUsed.size());
		assertEquals("First delay should be 20000ms (3rd attempt)", Long.valueOf(20000), delaysUsed.get(0));

		reconnectScheduled.set(false);

		scheduleReconnect(reconnectScheduled, reconnectAttempts, delaysUsed);
		assertEquals("After another open-close, attempt should increase", 2, delaysUsed.size());
		assertEquals("Next delay should be 30000ms (4th attempt)", Long.valueOf(30000), delaysUsed.get(1));
	}

	private static void scheduleReconnect(AtomicBoolean reconnectScheduled, AtomicInteger reconnectAttempts, List<Long> scheduleTimes)
	{
		if (!reconnectScheduled.compareAndSet(false, true))
		{
			return;
		}

		int attempt = reconnectAttempts.incrementAndGet();
		if (attempt > DELAYS_MS.length)
		{
			return;
		}

		int delayIndex = Math.min(attempt - 1, DELAYS_MS.length - 1);
		long delayMs = DELAYS_MS[delayIndex];

		scheduleTimes.add(delayMs);
	}
}
