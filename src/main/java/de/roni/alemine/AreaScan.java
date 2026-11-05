package de.roni.alemine;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/** Zählt, was im geplanten Bereich liegt – für die Live-Anzeige im Menü. */
public record AreaScan(int total, int toMine, int empty, int lava, int water, int unbreakable, boolean tooBig) {

	/** Bis zu dieser Größe wird live gezählt (sonst ruckelt das Menü). */
	public static final int MAX_SCAN = 400_000;

	public static AreaScan scan(ClientLevel level, BlockPos origin, Direction fwd, int length, int width, int depth) {
		int total = length * width * depth;
		if (total > MAX_SCAN) return new AreaScan(total, 0, 0, 0, 0, 0, true);

		Direction right = fwd.getClockWise();
		int toMine = 0, empty = 0, lava = 0, water = 0, unbreakable = 0;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

		for (int f = 0; f < length; f++) {
			for (int r = 0; r < width; r++) {
				int x = origin.getX() + fwd.getStepX() * f + right.getStepX() * r;
				int z = origin.getZ() + fwd.getStepZ() * f + right.getStepZ() * r;
				for (int k = 1; k <= depth; k++) {
					m.set(x, origin.getY() - k, z);
					FluidState fl = level.getFluidState(m);
					if (!fl.isEmpty()) {
						if (fl.is(FluidTags.LAVA)) lava++;
						else if (fl.is(FluidTags.WATER)) water++;
						continue;
					}
					BlockState s = level.getBlockState(m);
					if (s.isAir() || s.getCollisionShape(level, m).isEmpty()) {
						empty++;
					} else if (s.getDestroySpeed(level, m) < 0) {
						unbreakable++;
					} else {
						toMine++;
					}
				}
			}
		}
		return new AreaScan(total, toMine, empty, lava, water, unbreakable, false);
	}
}
