package com.oliver.witchmod.blocks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.oliver.witchmod.Config;

/**
 * the ledger's own log — a small ring of recent nearby casts, capped at {@link Config#LEDGER_MAX_ENTRIES} and
 * PERSISTED with the world (nbt), so it survives reloads and never clears with time. new casts are pushed in
 * by {@link LedgerFeedback} at cast time (event-driven, no per-tick scanning); the renderer draws the 3d book.
 */
public final class LedgerBlockEntity extends BlockEntity {
    public record Row(String caster, String target, ResourceLocation effectId, String result, long gameTime,
                      boolean scribbled, String modifier) {}

    private final Deque<Row> rows = new ArrayDeque<>();

    public LedgerBlockEntity(BlockPos pos, BlockState state) {
        super(WitchModBlockEntities.LEDGER.get(), pos, state);
    }

    /** record a cast, dropping the oldest once over the cap; persisted immediately. */
    public void record(Row row) {
        rows.addLast(row);
        while (rows.size() > Config.LEDGER_MAX_ENTRIES.get()) {
            rows.removeFirst();
        }
        setChanged();
    }

    /** the kept casts, newest first (for display). */
    public List<Row> newestFirst() {
        List<Row> out = new ArrayList<>(rows);
        java.util.Collections.reverse(out);
        return out;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (Row r : rows) {
            CompoundTag c = new CompoundTag();
            c.putString("caster", r.caster());
            c.putString("target", r.target());
            c.putString("effect", r.effectId().toString());
            c.putString("result", r.result());
            c.putLong("time", r.gameTime());
            c.putBoolean("scribbled", r.scribbled());
            c.putString("modifier", r.modifier());
            list.add(c);
        }
        tag.put("Log", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rows.clear();
        ListTag list = tag.getList("Log", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(c.getString("effect"));
            if (id == null) {
                continue;
            }
            rows.addLast(new Row(c.getString("caster"), c.getString("target"), id, c.getString("result"),
                    c.getLong("time"), c.getBoolean("scribbled"), c.getString("modifier")));
        }
    }
}
