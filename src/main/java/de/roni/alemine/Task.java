package de.roni.alemine;

import net.minecraft.client.Minecraft;

/** Ein laufender AleMine-Auftrag (Graben oder XP-Farm). */
public interface Task {
	void tick(Minecraft mc);
	void cancel(Minecraft mc);
	void togglePause(Minecraft mc);
	boolean isPaused();
	boolean isFinished();
	String statusText();
}
