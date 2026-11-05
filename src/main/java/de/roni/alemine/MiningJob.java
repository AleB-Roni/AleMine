package de.roni.alemine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Gräbt einen Bereich ab: Länge (nach vorne) × Breite (nach rechts) × Tiefe (nach unten),
 * beginnend mit der Spalte, in der der Spieler steht.
 *
 * Ablauf pro „Scheibe“ (2 Blöcke hoch): Spieler gräbt 2 Blöcke unter sich weg, fällt rein und
 * läuft dann in Schlangenlinien durch die Scheibe – jeweils Kopf- und Fußblock vor sich abbauen,
 * einen Schritt gehen, am Ende der Reihe drehen. Dann die nächste Scheibe.
 */
public class MiningJob {

	private enum Kind { MOVE, MINE, FALL }

	private record Step(Kind kind, BlockPos pos, Direction face, boolean digDown) {
		Step(Kind kind, BlockPos pos, Direction face) { this(kind, pos, face, false); }
	}

	// Einstellungen
	private static final float MIN_HEALTH = 6.0f;      // unter 3 Herzen → Stopp
	private static final int MINE_TIMEOUT = 20 * 30;   // 30 s pro Block
	private static final int MOVE_TIMEOUT = 20 * 6;    // 6 s pro Schritt
	private static final int FALL_TIMEOUT = 20 * 4;
	private static final double CENTER_EPS = 0.12;

	private final Deque<Step> steps = new ArrayDeque<>();
	private final int length, width, depth;
	private final int totalBlocks;
	private int minedSteps = 0;
	private int stepTicks = 0;
	private int tickCounter = 0;
	private boolean paused = false;
	private boolean finished = false;
	private BlockPos lastMineTarget = null;

	public MiningJob(Minecraft mc, int length, int width, int depth) {
		this.length = length;
		this.width = width;
		this.depth = depth;
		this.totalBlocks = length * width * depth;

		LocalPlayer p = mc.player;
		Direction forward = horizontalFromYaw(p.getYRot());
		Direction right = forward.getClockWise();
		BlockPos origin = p.blockPosition();
		buildPlan(origin, forward, right);
	}

	// ------------------------------------------------------------------ Plan

	private void buildPlan(BlockPos origin, Direction fwd, Direction right) {
		int feetY = origin.getY();
		int remaining = depth;
		int cf = 0, cr = 0; // aktuelle Zelle (vorwärts, rechts)

		while (remaining > 0) {
			int h = Math.min(2, remaining);

			// mittig hinstellen und nach unten graben
			steps.add(new Step(Kind.MOVE, cell(origin, fwd, right, cf, cr, feetY), null));
			for (int k = 1; k <= h; k++) {
				steps.add(new Step(Kind.MINE, cell(origin, fwd, right, cf, cr, feetY - k), Direction.UP, true));
				steps.add(new Step(Kind.FALL, cell(origin, fwd, right, cf, cr, feetY - k), null));
			}
			feetY -= h;
			remaining -= h;

			// Schlangenlinie durch die Scheibe
			List<int[]> order = snake(cf, cr);
			int[] prev = order.get(0);
			for (int i = 1; i < order.size(); i++) {
				int[] next = order.get(i);
				Direction move = moveDir(prev, next, fwd, right);
				Direction face = move.getOpposite();
				// Kopfblock (bei 1er-Scheibe meist schon Luft → wird sofort übersprungen)
				steps.add(new Step(Kind.MINE, cell(origin, fwd, right, next[0], next[1], feetY + 1), face));
				steps.add(new Step(Kind.MINE, cell(origin, fwd, right, next[0], next[1], feetY), face));
				steps.add(new Step(Kind.MOVE, cell(origin, fwd, right, next[0], next[1], feetY), null));
				prev = next;
			}
			cf = prev[0];
			cr = prev[1];
		}
	}

	/** Alle Zellen der Fläche als Schlangenlinie, beginnend in der Ecke (cf, cr). */
	private List<int[]> snake(int cf, int cr) {
		List<int[]> out = new ArrayList<>(length * width);
		boolean fUp = cf == 0;
		boolean rUp = cr == 0;
		for (int ri = 0; ri < width; ri++) {
			int r = rUp ? ri : width - 1 - ri;
			for (int fi = 0; fi < length; fi++) {
				int f = fUp ? fi : length - 1 - fi;
				out.add(new int[]{f, r});
			}
			fUp = !fUp;
		}
		return out;
	}

	private static Direction moveDir(int[] a, int[] b, Direction fwd, Direction right) {
		if (b[0] > a[0]) return fwd;
		if (b[0] < a[0]) return fwd.getOpposite();
		if (b[1] > a[1]) return right;
		return right.getOpposite();
	}

	private static BlockPos cell(BlockPos o, Direction fwd, Direction right, int f, int r, int y) {
		return new BlockPos(
				o.getX() + fwd.getStepX() * f + right.getStepX() * r,
				y,
				o.getZ() + fwd.getStepZ() * f + right.getStepZ() * r);
	}

	private static Direction horizontalFromYaw(float yaw) {
		int i = Math.floorMod(Math.round(yaw / 90f), 4);
		return switch (i) {
			case 0 -> Direction.SOUTH;
			case 1 -> Direction.WEST;
			case 2 -> Direction.NORTH;
			default -> Direction.EAST;
		};
	}

	// ------------------------------------------------------------------ Tick

	public void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		ClientLevel level = mc.level;
		tickCounter++;

		if (paused) return;

		// Andere Menüs (Inventar, Chat …) → kurz anhalten. Pausemenü (Alt-Tab) stört nicht.
		if (mc.gui.screen() != null && !(mc.gui.screen() instanceof PauseScreen)) {
			releaseKeys(mc);
			return;
		}

		if (p.getHealth() < MIN_HEALTH && !p.getAbilities().instabuild) {
			fail(mc, "Wenig Leben – gestoppt.");
			return;
		}

		Step step = steps.peekFirst();
		if (step == null) {
			done(mc);
			return;
		}

		stepTicks++;
		switch (step.kind()) {
			case MOVE -> tickMove(mc, p, level, step);
			case MINE -> tickMine(mc, p, level, step);
			case FALL -> tickFall(mc, p, step);
		}

		if (tickCounter % 10 == 0 && !finished) {
			p.sendOverlayMessage(Component.literal("AleMine: " + statusText()).withStyle(ChatFormatting.GOLD));
		}
	}

	private void tickMove(Minecraft mc, LocalPlayer p, ClientLevel level, Step step) {
		BlockPos target = step.pos();

		// Ist der Weg frei? (Kies/Sand kann nachgerutscht sein)
		if (!passable(level, target.above())) {
			steps.addFirst(new Step(Kind.MINE, target.above(), Direction.UP));
			resetStep(mc);
			return;
		}
		if (!passable(level, target)) {
			steps.addFirst(new Step(Kind.MINE, target, Direction.UP));
			resetStep(mc);
			return;
		}
		// Boden vorhanden? Sonst Höhle/Loch → lieber stoppen statt reinfallen
		BlockPos floor = target.below();
		if (!solid(level, floor)) {
			fail(mc, "Kein Boden bei " + floor.toShortString() + " (Höhle?) – gestoppt.");
			return;
		}

		double tx = target.getX() + 0.5, tz = target.getZ() + 0.5;
		double dx = tx - p.getX(), dz = tz - p.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);

		if (dist < CENTER_EPS) {
			releaseKeys(mc);
			nextStep(mc);
			return;
		}
		if (stepTicks > MOVE_TIMEOUT) {
			fail(mc, "Hänge fest bei " + target.toShortString() + " – gestoppt.");
			return;
		}

		p.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
		p.setXRot(20f);
		mc.options.keyUp.setDown(true);
		mc.options.keyShift.setDown(dist < 0.6); // kurz vor dem Ziel schleichen → genau mittig stoppen
	}

	private void tickMine(Minecraft mc, LocalPlayer p, ClientLevel level, Step step) {
		BlockPos pos = step.pos();
		BlockState state = level.getBlockState(pos);

		if (dangerousFluidNear(level, pos, step.digDown())) {
			fail(mc, "Lava/Wasser neben " + pos.toShortString() + " – gestoppt.");
			return;
		}
		if (step.digDown() && !solid(level, pos.below()) && !passable(level, pos)) {
			fail(mc, "Unter " + pos.toShortString() + " ist kein Boden (Höhle?) – gestoppt.");
			return;
		}
		if (passable(level, pos)) {
			minedSteps++;
			nextStep(mc);
			return;
		}
		if (state.getDestroySpeed(level, pos) < 0) {
			fail(mc, "Unzerstörbarer Block bei " + pos.toShortString() + " – gestoppt.");
			return;
		}
		if (stepTicks > MINE_TIMEOUT) {
			fail(mc, "Block bei " + pos.toShortString() + " lässt sich nicht abbauen – gestoppt.");
			return;
		}

		releaseKeys(mc);
		if (!pos.equals(lastMineTarget)) {
			if (!selectBestTool(p, state)) {
				fail(mc, "Kein passendes Werkzeug in der Hotbar für " + state.getBlock().getName().getString() + ".");
				return;
			}
			lastMineTarget = pos;
		}
		lookAt(p, Vec3.atCenterOf(pos));
		mc.gameMode.continueDestroyBlock(pos, step.face());
	}

	private void tickFall(Minecraft mc, LocalPlayer p, Step step) {
		int expectY = step.pos().getY();
		if (p.onGround() && p.getY() < expectY + 0.5) {
			nextStep(mc);
			return;
		}
		if (stepTicks > FALL_TIMEOUT) {
			fail(mc, "Bin nicht runtergefallen – stehe ich auf einer Kante? Gestoppt.");
		}
	}

	// ------------------------------------------------------------------ Hilfen

	private boolean selectBestTool(LocalPlayer p, BlockState state) {
		Inventory inv = p.getInventory();
		int best = -1;
		float bestSpeed = -1;
		for (int i = 0; i < 9; i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && s.isDamageableItem() && s.getMaxDamage() - s.getDamageValue() <= 2) continue; // fast kaputt
			if (state.requiresCorrectToolForDrops() && (s.isEmpty() || !s.isCorrectToolForDrops(state))) continue;
			float speed = s.isEmpty() ? 1f : s.getDestroySpeed(state);
			if (speed > bestSpeed) {
				bestSpeed = speed;
				best = i;
			}
		}
		if (best < 0) return p.getAbilities().instabuild;
		if (inv.getSelectedSlot() != best) inv.setSelectedSlot(best);
		return true;
	}

	private static void lookAt(LocalPlayer p, Vec3 target) {
		Vec3 eye = p.getEyePosition();
		double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
		double horiz = Math.sqrt(dx * dx + dz * dz);
		p.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
		p.setXRot((float) -Math.toDegrees(Math.atan2(dy, horiz)));
	}

	private static boolean passable(ClientLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		return level.getFluidState(pos).isEmpty() && (s.isAir() || s.getCollisionShape(level, pos).isEmpty());
	}

	private static boolean solid(ClientLevel level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	private static boolean dangerousFluidNear(ClientLevel level, BlockPos pos, boolean digDown) {
		if (isFluid(level, pos)) return true;
		for (Direction d : Direction.values()) {
			if (d == Direction.DOWN && !digDown) continue; // darunter wird erst später gegraben
			if (isFluid(level, pos.relative(d))) return true;
		}
		return false;
	}

	private static boolean isFluid(ClientLevel level, BlockPos pos) {
		FluidState f = level.getFluidState(pos);
		return !f.isEmpty() && (f.is(FluidTags.LAVA) || f.is(FluidTags.WATER));
	}

	private void nextStep(Minecraft mc) {
		steps.pollFirst();
		resetStep(mc);
	}

	private void resetStep(Minecraft mc) {
		stepTicks = 0;
		lastMineTarget = null;
	}

	private static void releaseKeys(Minecraft mc) {
		mc.options.keyUp.setDown(false);
		mc.options.keyShift.setDown(false);
	}

	private void done(Minecraft mc) {
		releaseKeys(mc);
		finished = true;
		AleMineClient.say("Fertig! " + length + "×" + width + "×" + depth + " ausgehoben.", ChatFormatting.GREEN);
	}

	private void fail(Minecraft mc, String reason) {
		cancel(mc);
		AleMineClient.say(reason + " Weiter mit /alemine start …", ChatFormatting.RED);
	}

	// ------------------------------------------------------------------ Steuerung

	public void cancel(Minecraft mc) {
		releaseKeys(mc);
		if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
		finished = true;
	}

	public void togglePause(Minecraft mc) {
		paused = !paused;
		if (paused) {
			releaseKeys(mc);
			if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
		}
		resetStep(mc);
	}

	public boolean isPaused() { return paused; }
	public boolean isFinished() { return finished; }
	public int totalBlocks() { return totalBlocks; }

	public String statusText() {
		// minedSteps zählt alle Abbau-Schritte (inkl. Kopfblöcke, die schon Luft waren)
		int planned = Math.max(1, minedSteps + (int) steps.stream().filter(s -> s.kind() == Kind.MINE).count());
		int pct = Math.min(100, minedSteps * 100 / planned);
		return pct + "% · noch " + steps.size() + " Schritte" + (paused ? " · PAUSE" : "");
	}
}
