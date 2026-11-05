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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Gräbt einen beliebigen Quader ab. Der Spieler muss in einer Eck-Spalte des Bereichs stehen –
 * entweder auf Fußhöhe im Bereich (Tunnel/Raum nach vorne, bis 5 hoch) oder direkt darüber (runter graben).
 *
 * Ebene auf Fußhöhe: in Schlangenlinien laufen, vor sich jeweils die ganze Spalte (oben → unten) abbauen.
 * Darunter: immer 2 Blöcke runtergraben, reinfallen, wieder Schlangenlinie.
 * Fehlt Boden (Schlucht, Höhle), wird ein Block aus der Hotbar gesetzt.
 */
public class MiningJob implements Task {

	private enum Kind { MOVE, MINE, FALL, PLACE }

	private record Step(Kind kind, BlockPos pos, Direction face, boolean digDown) {
		Step(Kind kind, BlockPos pos, Direction face) { this(kind, pos, face, false); }
	}

	public static final int MAX_ABOVE_FEET = 4; // Fußhöhe + 4 = 5 Blöcke hoch

	private static final float MIN_HEALTH = 6.0f;
	private static final int MINE_TIMEOUT = 20 * 30;
	private static final int MOVE_TIMEOUT = 20 * 6;
	private static final int FALL_TIMEOUT = 20 * 4;
	private static final int PLACE_TIMEOUT = 20 * 3;
	private static final double CENTER_EPS = 0.12;

	private static final Item[] FILLER = {
			Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.DIRT, Items.NETHERRACK, Items.STONE,
			Items.DEEPSLATE, Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.TUFF, Items.BLACKSTONE
	};

	private final Deque<Step> steps = new ArrayDeque<>();
	private final Area area;
	private String error;
	private int minedSteps = 0;
	private int placed = 0;
	private int stepTicks = 0;
	private int tickCounter = 0;
	private boolean paused = false;
	private boolean finished = false;
	private BlockPos lastMineTarget = null;

	public MiningJob(Minecraft mc, Area area) {
		this.area = area;
		this.error = plan(mc.player);
		if (error != null) finished = true;
	}

	public Area area() { return area; }
	/** Fehlermeldung, falls der Auftrag so nicht starten kann (sonst null). */
	public String error() { return error; }

	/** Nächster Block, an dem gearbeitet wird – für die Vorschau. */
	public BlockPos currentTarget() {
		Step s = steps.peekFirst();
		return s == null || s.kind() == Kind.FALL ? null : s.pos();
	}

	// ------------------------------------------------------------------ Plan

	/** Prüft, ob der Spieler von hier aus starten kann. null = alles ok, sonst Grund. */
	public static String check(LocalPlayer p, Area area) {
		if (area == null) return "Erst Ecke 1 und Ecke 2 markieren.";
		BlockPos me = p.blockPosition();
		int px = me.getX(), pz = me.getZ(), feet = me.getY();
		BlockPos min = area.min(), max = area.max();
		boolean cornerX = px == min.getX() || px == max.getX();
		boolean cornerZ = pz == min.getZ() || pz == max.getZ();
		boolean inside = px >= min.getX() && px <= max.getX() && pz >= min.getZ() && pz <= max.getZ();
		if (!inside || !cornerX || !cornerZ) {
			return "Stell dich in eine Ecke des Bereichs (in die Eck-Spalte), dann starten.";
		}
		boolean above = max.getY() >= feet;
		if (above && min.getY() > feet) return "Der Bereich muss auf deiner Fußhöhe oder direkt darunter beginnen.";
		if (above && max.getY() > feet + MAX_ABOVE_FEET) return "Höchstens " + (MAX_ABOVE_FEET + 1) + " Blöcke hoch ab deinen Füßen.";
		if (!above && max.getY() != feet - 1) return "Stell dich direkt auf den Bereich (oberste Ebene = Block unter dir) oder in ihn rein.";
		return null;
	}

	private String plan(LocalPlayer p) {
		String err = check(p, area);
		if (err != null) return err;
		BlockPos me = p.blockPosition();
		int px = me.getX(), pz = me.getZ(), feet = me.getY();
		BlockPos min = area.min(), max = area.max();

		boolean above = max.getY() >= feet;

		// Achsen ab der Ecke, in der der Spieler steht
		Direction xDir = px == min.getX() ? Direction.EAST : Direction.WEST;
		Direction zDir = pz == min.getZ() ? Direction.SOUTH : Direction.NORTH;
		int xLen = area.sizeX(), zLen = area.sizeZ();
		Direction facing = horizontalFromYaw(p.getYRot());
		boolean fwdIsX = facing.getAxis() == Direction.Axis.X;
		Direction fwd = fwdIsX ? xDir : zDir;
		Direction side = fwdIsX ? zDir : xDir;
		int length = fwdIsX ? xLen : zLen;
		int width = fwdIsX ? zLen : xLen;
		BlockPos origin = new BlockPos(px, feet, pz);

		int cf = 0, cr = 0;

		// 1) Ebene(n) ab Fußhöhe nach oben
		if (above) {
			int top = Math.max(max.getY(), feet + 1); // Kopfhöhe muss frei sein, um zu laufen
			for (int y = top; y >= feet + 2; y--) {
				steps.add(new Step(Kind.MINE, cell(origin, fwd, side, 0, 0, y), Direction.DOWN));
			}
			List<int[]> order = snake(cf, cr, length, width);
			int[] prev = order.get(0);
			for (int i = 1; i < order.size(); i++) {
				int[] next = order.get(i);
				Direction face = moveDir(prev, next, fwd, side).getOpposite();
				for (int y = top; y >= feet; y--) {
					steps.add(new Step(Kind.MINE, cell(origin, fwd, side, next[0], next[1], y), face));
				}
				steps.add(new Step(Kind.MOVE, cell(origin, fwd, side, next[0], next[1], feet), null));
				prev = next;
			}
			cf = prev[0];
			cr = prev[1];
		}

		// 2) Ebenen unter den Füßen – immer 2 auf einmal
		int feetY = feet;
		int remaining = Math.min(feet - 1, max.getY()) - min.getY() + 1;
		while (remaining > 0) {
			int h = Math.min(2, remaining);
			steps.add(new Step(Kind.MOVE, cell(origin, fwd, side, cf, cr, feetY), null));
			for (int k = 1; k <= h; k++) {
				steps.add(new Step(Kind.MINE, cell(origin, fwd, side, cf, cr, feetY - k), Direction.UP, true));
				steps.add(new Step(Kind.FALL, cell(origin, fwd, side, cf, cr, feetY - k), null));
			}
			feetY -= h;
			remaining -= h;

			List<int[]> order = snake(cf, cr, length, width);
			int[] prev = order.get(0);
			for (int i = 1; i < order.size(); i++) {
				int[] next = order.get(i);
				Direction face = moveDir(prev, next, fwd, side).getOpposite();
				steps.add(new Step(Kind.MINE, cell(origin, fwd, side, next[0], next[1], feetY + 1), face));
				steps.add(new Step(Kind.MINE, cell(origin, fwd, side, next[0], next[1], feetY), face));
				steps.add(new Step(Kind.MOVE, cell(origin, fwd, side, next[0], next[1], feetY), null));
				prev = next;
			}
			cf = prev[0];
			cr = prev[1];
		}
		return null;
	}

	/** Alle Zellen der Fläche als Schlangenlinie, beginnend in der Ecke (cf, cr). */
	private static List<int[]> snake(int cf, int cr, int length, int width) {
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

	private static Direction moveDir(int[] a, int[] b, Direction fwd, Direction side) {
		if (b[0] > a[0]) return fwd;
		if (b[0] < a[0]) return fwd.getOpposite();
		if (b[1] > a[1]) return side;
		return side.getOpposite();
	}

	static BlockPos cell(BlockPos o, Direction fwd, Direction side, int f, int r, int y) {
		return new BlockPos(
				o.getX() + fwd.getStepX() * f + side.getStepX() * r,
				y,
				o.getZ() + fwd.getStepZ() * f + side.getStepZ() * r);
	}

	static Direction horizontalFromYaw(float yaw) {
		int i = Math.floorMod(Math.round(yaw / 90f), 4);
		return switch (i) {
			case 0 -> Direction.SOUTH;
			case 1 -> Direction.WEST;
			case 2 -> Direction.NORTH;
			default -> Direction.EAST;
		};
	}

	// ------------------------------------------------------------------ Tick

	@Override
	public void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		ClientLevel level = mc.level;
		tickCounter++;

		if (paused || finished) return;

		// Andere Menüs (Inventar, Chat …) → kurz anhalten. Pausemenü (Alt-Tab) und AleMine-Menü stören nicht.
		if (mc.gui.screen() != null && !(mc.gui.screen() instanceof PauseScreen) && !(mc.gui.screen() instanceof AleMineScreen)) {
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
			case PLACE -> tickPlace(mc, p, level, step);
		}

		if (tickCounter % 10 == 0 && !finished) {
			p.sendOverlayMessage(Component.literal("AleMine: " + statusText()).withStyle(ChatFormatting.GOLD));
		}
	}

	private void tickMove(Minecraft mc, LocalPlayer p, ClientLevel level, Step step) {
		BlockPos target = step.pos();

		// Kies/Sand nachgerutscht?
		if (!passable(level, target.above())) {
			steps.addFirst(new Step(Kind.MINE, target.above(), Direction.UP));
			resetStep();
			return;
		}
		if (!passable(level, target)) {
			steps.addFirst(new Step(Kind.MINE, target, Direction.UP));
			resetStep();
			return;
		}
		// Kein Boden? → Block setzen (oder stoppen, wenn ausgeschaltet)
		BlockPos floor = target.below();
		if (!solid(level, floor)) {
			if (isLava(level, floor)) {
				fail(mc, "Lava unter " + target.toShortString() + " – gestoppt.");
				return;
			}
			if (!AleMineConfig.bridgeGaps) {
				fail(mc, "Kein Boden bei " + floor.toShortString() + " – gestoppt.");
				return;
			}
			releaseKeys(mc);
			steps.addFirst(new Step(Kind.PLACE, floor, null));
			resetStep();
			return;
		}

		double tx = target.getX() + 0.5, tz = target.getZ() + 0.5;
		double dx = tx - p.getX(), dz = tz - p.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);

		if (dist < CENTER_EPS) {
			releaseKeys(mc);
			nextStep();
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
			fail(mc, (AleMineConfig.stopOnWater ? "Lava/Wasser" : "Lava") + " neben " + pos.toShortString() + " – gestoppt.");
			return;
		}
		// Beim Runtergraben: darunter Luft? Erst Boden setzen, sonst fällt er in die Höhle.
		if (step.digDown() && !passable(level, pos) && !solid(level, pos.below())) {
			if (!AleMineConfig.bridgeGaps) {
				fail(mc, "Unter " + pos.toShortString() + " ist kein Boden (Höhle?) – gestoppt.");
				return;
			}
			steps.addFirst(new Step(Kind.PLACE, pos.below(), null));
			resetStep();
			return;
		}
		if (passable(level, pos)) {
			minedSteps++;
			nextStep();
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
			nextStep();
			return;
		}
		if (stepTicks > FALL_TIMEOUT) {
			fail(mc, "Bin nicht runtergefallen – stehe ich auf einer Kante? Gestoppt.");
		}
	}

	/** Setzt einen Füllblock an step.pos (klickt dafür auf einen festen Nachbarblock). */
	private void tickPlace(Minecraft mc, LocalPlayer p, ClientLevel level, Step step) {
		BlockPos target = step.pos();
		if (solid(level, target)) {
			placed++;
			nextStep();
			return;
		}
		if (stepTicks > PLACE_TIMEOUT) {
			fail(mc, "Konnte bei " + target.toShortString() + " keinen Block setzen – gestoppt.");
			return;
		}
		if (stepTicks % 5 != 1) return; // nicht jeden Tick klicken, kurz auf den Server warten

		if (!selectFiller(p)) {
			fail(mc, "Lücke bei " + target.toShortString() + ", aber keine Blöcke (z. B. Bruchstein) in der Hotbar.");
			return;
		}
		for (Direction d : Direction.values()) {
			BlockPos n = target.relative(d);
			if (!solid(level, n)) continue;
			Direction face = d.getOpposite(); // Seite von n, die zu target zeigt
			Vec3 hit = Vec3.atCenterOf(n).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
			lookAt(p, hit);
			mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, n, false));
			return;
		}
		fail(mc, "Kein Halt zum Block-Setzen bei " + target.toShortString() + " – gestoppt.");
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

	private static boolean selectFiller(LocalPlayer p) {
		Inventory inv = p.getInventory();
		int slot = -1;
		// bevorzugt einfache Blöcke wie Bruchstein/Erde …
		outer:
		for (Item want : FILLER) {
			for (int i = 0; i < 9; i++) {
				if (inv.getItem(i).is(want)) {
					slot = i;
					break outer;
				}
			}
		}
		// … sonst irgendein voller, nicht fallender Block ohne Inhalt (keine Truhen o. Ä.)
		if (slot < 0) {
			for (int i = 0; i < 9; i++) {
				ItemStack s = inv.getItem(i);
				if (s.getItem() instanceof BlockItem bi) {
					Block b = bi.getBlock();
					if (b instanceof FallingBlock || b instanceof EntityBlock) continue;
					if (!b.defaultBlockState().isCollisionShapeFullBlock(p.level(), BlockPos.ZERO)) continue;
					slot = i;
					break;
				}
			}
		}
		if (slot < 0) return false;
		if (inv.getSelectedSlot() != slot) inv.setSelectedSlot(slot);
		return true;
	}

	private static void lookAt(LocalPlayer p, Vec3 target) {
		Vec3 eye = p.getEyePosition();
		double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
		double horiz = Math.sqrt(dx * dx + dz * dz);
		p.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
		p.setXRot((float) -Math.toDegrees(Math.atan2(dy, horiz)));
	}

	static boolean passable(ClientLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		return level.getFluidState(pos).isEmpty() && (s.isAir() || s.getCollisionShape(level, pos).isEmpty());
	}

	static boolean solid(ClientLevel level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	private static boolean isLava(ClientLevel level, BlockPos pos) {
		return level.getFluidState(pos).is(FluidTags.LAVA);
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
		return !f.isEmpty() && (f.is(FluidTags.LAVA) || (AleMineConfig.stopOnWater && f.is(FluidTags.WATER)));
	}

	private void nextStep() {
		steps.pollFirst();
		resetStep();
	}

	private void resetStep() {
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
		AleMineClient.say("Fertig! " + area.sizeText() + " ausgehoben" + (placed > 0 ? ", " + placed + " Lücken gefüllt." : "."), ChatFormatting.GREEN);
	}

	private void fail(Minecraft mc, String reason) {
		cancel(mc);
		AleMineClient.say(reason, ChatFormatting.RED);
	}

	// ------------------------------------------------------------------ Steuerung

	@Override
	public void cancel(Minecraft mc) {
		releaseKeys(mc);
		if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
		finished = true;
	}

	@Override
	public void togglePause(Minecraft mc) {
		paused = !paused;
		if (paused) {
			releaseKeys(mc);
			if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
		}
		resetStep();
	}

	@Override public boolean isPaused() { return paused; }
	@Override public boolean isFinished() { return finished; }

	@Override
	public String statusText() {
		int planned = Math.max(1, minedSteps + (int) steps.stream().filter(s -> s.kind() == Kind.MINE).count());
		int pct = Math.min(100, minedSteps * 100 / planned);
		return pct + "% · noch " + steps.size() + " Schritte" + (placed > 0 ? " · " + placed + " Lücken gefüllt" : "")
				+ (paused ? " · PAUSE" : "");
	}
}
