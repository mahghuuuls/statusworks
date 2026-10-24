package com.mahghuuuls.statusworks.core.mark;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.storage.WorldSavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The mark store's file, {@code data/statusworks_marks.dat} of the save, held in the overworld's map storage.
 * Minecraft creates it by reflection with the name and saves it with the world while it is marked dirty.
 * Warnings from reading are kept until the store takes them.
 */
public final class MarkSavedData extends WorldSavedData {

    public static final String NAME = "statusworks_marks";

    final MarkRecords records = new MarkRecords();
    /** Data from a newer save format, kept unchanged and written back as it was. */
    @Nullable NBTTagCompound newer;
    final List<String> readWarnings = new ArrayList<>();

    public MarkSavedData(String name) {
        super(name);
    }

    public MarkSavedData() {
        this(NAME);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        if (MarkCodec.isNewer(nbt)) {
            newer = nbt.copy();
            readWarnings.add("mark data has save format " + nbt.getInteger("v") + ", newer than "
                    + MarkCodec.VERSION + "; it is kept unchanged and no marks are loaded or created");
            return;
        }
        MarkCodec.read(nbt, records, readWarnings::add);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        if (newer != null) {
            return newer.copy();
        }
        NBTTagCompound data = MarkCodec.write(records);
        for (String key : data.getKeySet()) {
            compound.setTag(key, data.getTag(key));
        }
        return compound;
    }
}
