package de.roni.alemine;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Ein quaderförmiger Bereich (beide Ecken eingeschlossen). */
public record Area(BlockPos min, BlockPos max) {

	public enum Mode { DOWN, FORWARD, MARKED }

	public static Area of(BlockPos a, BlockPos b) {
		return new Area(
				new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
				new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())));
	}

	/**
	 * Bereich relativ zum Spieler: startet in der Spalte, in der er steht,
	 * geht in Blickrichtung (Länge) und zur gewählten Seite (Breite).
	 * DOWN: unter den Füßen nach unten. FORWARD: ab Fußhöhe nach oben (Tunnel/Raum).
	 */
	public static Area relative(LocalPlayer p, Mode mode, int length, int width, int depth, int height, boolean left) {
		Direction fwd = MiningJob.horizontalFromYaw(p.getYRot());
		Direction side = left ? fwd.getCounterClockWise() : fwd.getClockWise();
		BlockPos o = p.blockPosition();
		int fx = o.getX() + fwd.getStepX() * (length - 1) + side.getStepX() * (width - 1);
		int fz = o.getZ() + fwd.getStepZ() * (length - 1) + side.getStepZ() * (width - 1);
		int y1, y2;
		if (mode == Mode.FORWARD) {
			y1 = o.getY();
			y2 = o.getY() + height - 1;
		} else {
			y1 = o.getY() - 1;
			y2 = o.getY() - depth;
		}
		return of(new BlockPos(o.getX(), y1, o.getZ()), new BlockPos(fx, y2, fz));
	}

	public int sizeX() { return max.getX() - min.getX() + 1; }
	public int sizeY() { return max.getY() - min.getY() + 1; }
	public int sizeZ() { return max.getZ() - min.getZ() + 1; }
	public long volume() { return (long) sizeX() * sizeY() * sizeZ(); }

	public boolean contains(BlockPos p) {
		return p.getX() >= min.getX() && p.getX() <= max.getX()
				&& p.getY() >= min.getY() && p.getY() <= max.getY()
				&& p.getZ() >= min.getZ() && p.getZ() <= max.getZ();
	}

	public AABB toAabb() {
		return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
	}

	public String sizeText() {
		return sizeX() + " × " + sizeZ() + " × " + sizeY() + " (B×L×H)";
	}
}
