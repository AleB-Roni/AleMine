package de.roni.alemine;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * XP-Farm-Modus: schlägt in festem Abstand auf das, was im Fadenkreuz ist
 * (z. B. Rüstungsständer oder Mobs in der Farm). Der Spieler bewegt sich nicht.
 */
public class XpFarmJob implements Task {

	private static final float MIN_HEALTH = 6.0f;

	private int counter = 0;
	private int hits = 0;
	private int tickCounter = 0;
	private int startLevel = -1;
	private boolean paused = false;
	private boolean finished = false;
	private String lastTarget = "–";

	@Override
	public void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		tickCounter++;
		if (startLevel < 0) startLevel = p.experienceLevel;
		if (paused) return;
		if (mc.gui.screen() != null && !(mc.gui.screen() instanceof PauseScreen)) return;

		if (AleMineConfig.xpStopOnLowHealth && p.getHealth() < MIN_HEALTH && !p.getAbilities().instabuild) {
			finished = true;
			AleMineClient.say("XP-Farm gestoppt – wenig Leben.", ChatFormatting.RED);
			return;
		}

		Entity target = currentTarget(mc);
		lastTarget = target == null ? "kein Ziel im Fadenkreuz" : target.getName().getString();

		counter++;
		if (counter >= AleMineConfig.hitInterval && target != null) {
			counter = 0;
			mc.gameMode.attack(p, target);
			p.swing(InteractionHand.MAIN_HAND);
			hits++;
		}

		if (tickCounter % 10 == 0) {
			p.sendOverlayMessage(Component.literal("AleMine XP: " + statusText()).withStyle(ChatFormatting.GREEN));
		}
	}

	public static Entity currentTarget(Minecraft mc) {
		HitResult hr = mc.hitResult;
		if (hr instanceof EntityHitResult ehr && hr.getType() == HitResult.Type.ENTITY) return ehr.getEntity();
		return null;
	}

	@Override
	public void cancel(Minecraft mc) {
		finished = true;
	}

	@Override
	public void togglePause(Minecraft mc) {
		paused = !paused;
		counter = 0;
	}

	@Override public boolean isPaused() { return paused; }
	@Override public boolean isFinished() { return finished; }
	public int hits() { return hits; }

	@Override
	public String statusText() {
		Minecraft mc = Minecraft.getInstance();
		int lvl = mc.player == null ? 0 : mc.player.experienceLevel;
		int gained = startLevel < 0 ? 0 : lvl - startLevel;
		return hits + " Schläge · Level " + lvl + (gained > 0 ? " (+" + gained + ")" : "")
				+ " · Ziel: " + lastTarget + (paused ? " · PAUSE" : "");
	}
}
