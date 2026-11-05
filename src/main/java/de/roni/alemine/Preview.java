package de.roni.alemine;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;

/** Zeichnet den Bereich als Rahmen in die Welt (durch Blöcke hindurch sichtbar). */
public final class Preview {

	private static final int AREA = ARGB.color(255, 255, 170, 0);   // orange
	private static final int RUNNING = ARGB.color(255, 85, 255, 85); // grün
	private static final int POS1 = ARGB.color(255, 85, 255, 255);   // türkis
	private static final int POS2 = ARGB.color(255, 255, 85, 255);   // pink
	private static final int TARGET = ARGB.color(255, 255, 60, 60);  // rot

	private Preview() {}

	public static void register() {
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> emit());
	}

	/** Der Bereich, den „Start“ gerade abbauen würde (oder null). */
	public static Area plannedArea() {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) return null;
		if (AleMineConfig.mineMode == Area.Mode.MARKED) return Selection.area();
		return Area.relative(p, AleMineConfig.mineMode, AleMineConfig.length, AleMineConfig.width,
				AleMineConfig.depth, AleMineConfig.height, AleMineConfig.leftSide);
	}

	private static void emit() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;

		if (AleMineClient.job instanceof MiningJob job) {
			box(job.area().toAabb(), RUNNING, 3f);
			BlockPos t = job.currentTarget();
			if (t != null) box(new AABB(t), TARGET, 2f);
			return;
		}
		if (!AleMineConfig.preview && !(mc.gui.screen() instanceof AleMineScreen)) return;
		if (AleMineClient.job != null) return; // XP-Farm läuft

		if (Selection.pos1() != null) box(new AABB(Selection.pos1()).inflate(0.02), POS1, 2f);
		if (Selection.pos2() != null) box(new AABB(Selection.pos2()).inflate(0.02), POS2, 2f);

		Area a = plannedArea();
		if (a != null && a.volume() <= 2_000_000) box(a.toAabb().inflate(0.01), AREA, 3f);
	}

	private static void box(AABB aabb, int color, float width) {
		Gizmos.cuboid(aabb, GizmoStyle.stroke(color, width)).setAlwaysOnTop();
	}
}
