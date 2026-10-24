package com.mahghuuuls.statusworks.core.mark;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkRecordsTest {

    private static final UUID A = new UUID(1, 1);
    private static final UUID B = new UUID(2, 2);
    private static final UUID X = new UUID(3, 3);
    private static final UUID Y = new UUID(4, 4);
    private static final UUID Z = new UUID(5, 5);

    private static MarkRecord mark(MarkRecords records, String id, UUID source, UUID target) {
        MarkRecord r = new MarkRecord(id, source, target, records.takeOrder(), "s", "t");
        records.add(r);
        return r;
    }

    @Test
    void bothIndexesFindEveryMarkInCreationOrder() {
        MarkRecords records = new MarkRecords();
        MarkRecord ax = mark(records, "t:m", A, X);
        MarkRecord ay = mark(records, "t:m", A, Y);
        MarkRecord bx = mark(records, "t:m", B, X);
        MarkRecord other = mark(records, "t:other", A, Z);
        assertSame(ax, records.find("t:m", A, X));
        assertNull(records.find("t:other", A, X), "one mark per source, target, and definition");
        assertEquals(2, records.ofSource(A, "t:m").size());
        assertSame(ax, records.ofSource(A, "t:m").get(0), "the first is the oldest");
        assertEquals(2, records.ofTarget(X, "t:m").size(), "two sources mark X independently");
        assertEquals(3, records.asSource(A).size());
        assertTrue(records.participates(Z));
        assertEquals(2, records.involving(X).size());
        records.remove(ax);
        assertNull(records.find("t:m", A, X));
        assertSame(ay, records.ofSource(A, "t:m").get(0), "the next oldest");
        assertEquals(1, records.ofTarget(X, "t:m").size());
        records.remove(other);
        assertFalse(records.participates(Z), "an index entry goes with its last mark");
        assertSame(bx, records.ofTarget(X, "t:m").get(0));
    }

    @Test
    void creationOrderOnlyGrows() {
        MarkRecords records = new MarkRecords();
        MarkRecord first = mark(records, "t:m", A, X);
        records.remove(first);
        MarkRecord second = mark(records, "t:m", A, X);
        assertTrue(second.order > first.order);
    }

    @Test
    void theSaveFormatRoundTripsEveryField() {
        MarkRecords records = new MarkRecords();
        MarkRecord r = mark(records, "t:m", A, X);
        r.age = 42;
        r.endAge = 600;
        r.inactiveTicks = 17;
        r.active = true;
        r.sourceName = "Steve";
        r.targetName = "Zombie";
        MarkRecord none = mark(records, "t:none", B, Y);
        records.remove(mark(records, "t:gone", B, Z));
        NBTTagCompound nbt = MarkCodec.write(records);
        assertEquals(MarkCodec.VERSION, nbt.getInteger("v"));

        MarkRecords back = new MarkRecords();
        List<String> warnings = new ArrayList<>();
        MarkCodec.read(nbt, back, warnings::add);
        assertTrue(warnings.isEmpty());
        assertEquals(2, back.size());
        MarkRecord copy = back.find("t:m", A, X);
        assertNotNull(copy);
        assertEquals(r.order, copy.order);
        assertEquals(42, copy.age);
        assertEquals(600, copy.endAge);
        assertEquals(17, copy.inactiveTicks);
        assertTrue(copy.active);
        assertEquals("Steve", copy.sourceName);
        assertEquals("Zombie", copy.targetName);
        assertEquals(-1, back.find("t:none", B, Y).endAge);
        assertEquals(none.order, back.find("t:none", B, Y).order);
        assertEquals(records.nextOrder(), back.nextOrder(), "order numbers are never reused after a load");
    }

    @Test
    void aBrokenEntryIsDroppedAndTheRestLoad() {
        MarkRecords records = new MarkRecords();
        mark(records, "t:m", A, X);
        NBTTagCompound nbt = MarkCodec.write(records);
        NBTTagList list = nbt.getTagList("marks", 10);
        NBTTagCompound broken = new NBTTagCompound();
        broken.setString("id", "t:m");
        list.appendTag(broken);
        MarkRecords back = new MarkRecords();
        List<String> warnings = new ArrayList<>();
        MarkCodec.read(nbt, back, warnings::add);
        assertEquals(1, back.size());
        assertEquals(1, warnings.size());
    }

    @Test
    void newerDataIsRecognizedAndKeptAsItWas() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("v", MarkCodec.VERSION + 1);
        nbt.setString("future", "data");
        assertTrue(MarkCodec.isNewer(nbt));
        MarkSavedData data = new MarkSavedData();
        data.readFromNBT(nbt);
        assertTrue(data.records.isEmpty());
        assertEquals(1, data.readWarnings.size());
        NBTTagCompound written = data.writeToNBT(new NBTTagCompound());
        assertEquals("data", written.getString("future"));
        assertEquals(MarkCodec.VERSION + 1, written.getInteger("v"));
    }
}
