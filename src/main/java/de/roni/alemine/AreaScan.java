package de.roni.alemine;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/** Zählt, was im geplanten Bereich liegt – für die Live-Anzeige im Menü. */
public record AreaScan(long total, int toMine, int empty, int lava, int water, int unbreakable, boolean tooBig) {

	/** Bis zu dieser Größe wird live gezählt (sonst ruckelt das Menü). */
	public static final int MAX_SCAN = 400_000;

	public static AreaScan scan(ClientLevel level, Area a) {
		long total = a.volume();
		if (total > MAX_SCAN) return new AreaScan(total, 0, 0, 0, 0, 0, true);

		int toMine = 0, empty = 0, lava = 0, water = 0, unbreakable = 0;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = a.min().getX(); x <= a.max().getX(); x++) {
			for (int z = a.min().getZ(); z <= a.max().getZ(); z++) {
				for (int y = a.min().getY(); y <= a.max().getY(); y++) {
					m.set(x, y, z);
					FluidState fl = level.getFluidState(m);
					if (!fl.isEmpty()) {
						if (fl.is(FluidTags.LAVA)) lava++;
						else if (fl.is(FluidTags.WATER)) water++;
						continue;
					}
					BlockState s = level.getBlockState(m);
					if (s.isAir() || s.getCollisionShape(level, m).isEmpty()) empty++;
					else if (s.getDestroySpeed(level, m) < 0) unbreakable++;
					else toMine++;
				}
			}
		}
		return new AreaScan(total, toMine, empty, lava, water, unbreakable, false);
	}
}
