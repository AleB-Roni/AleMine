package de.roni.alemine;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Selbst markierter Bereich: Ecke 1 und Ecke 2 (angeschauter Block). */
public final class Selection {

	private static BlockPos pos1;
	private static BlockPos pos2;

	private Selection() {}

	public static BlockPos pos1() { return pos1; }
	public static BlockPos pos2() { return pos2; }

	public static Area area() {
		return pos1 == null || pos2 == null ? null : Area.of(pos1, pos2);
	}

	/** Angeschauter Block, sonst der Block, in dem der Spieler steht. */
	public static BlockPos aimedBlock() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.hitResult instanceof BlockHitResult bhr && mc.hitResult.getType() == HitResult.Type.BLOCK) {
			return bhr.getBlockPos();
		}
		return mc.player == null ? null : mc.player.blockPosition();
	}

	public static void set(int which) {
		BlockPos b = aimedBlock();
		if (b == null) return;
		if (which == 1) pos1 = b;
		else pos2 = b;
		AleMineConfig.mineMode = Area.Mode.MARKED;
		AleMineConfig.save();
		Area a = area();
		AleMineClient.say("Ecke " + which + " = " + b.toShortString()
				+ (a != null ? "  →  " + a.sizeText() + " = " + a.volume() + " Blöcke" : "  (jetzt noch die andere Ecke)"),
				ChatFormatting.AQUA);
	}

	public static void clear() {
		pos1 = null;
		pos2 = null;
		AleMineClient.say("Markierung gelöscht.", ChatFormatting.GRAY);
	}
}
