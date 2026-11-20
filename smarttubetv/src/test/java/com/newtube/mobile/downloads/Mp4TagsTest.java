package com.newtube.mobile.downloads;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Mp4TagsTest {
    private static final byte[] FTYP = Mp4Tags.box("ftyp", ascii("isom\0\0\0\0isom"));
    private static final byte[] MOOV = Mp4Tags.box("moov", Mp4Tags.box("mvhd", new byte[100]));
    private static final byte[] MDAT = Mp4Tags.box("mdat", filled(5000, (byte) 0x5A));

    /** The muxer's usual layout: moov at the front, followed by the space it reserved. */
    @Test
    public void tagsGoIntoTheFreeBoxAfterAFrontMoov() throws Exception {
        Memory file = new Memory(FTYP, MOOV, Mp4Tags.box("free", new byte[4000]), MDAT);
        long mdatAt = file.data.length - MDAT.length;

        assertTrue(Mp4Tags.apply(file, tags()));

        assertEquals(FTYP.length + MOOV.length + 8 + 4000 + MDAT.length, file.data.length);
        assertEquals(Arrays.asList("ftyp", "moov", "free", "mdat"), topLevel(file.data));
        assertMdatUntouched(file.data, mdatAt);
        assertTagged(boxAt(file.data, FTYP.length));
    }

    @Test
    public void aMoovAtTheEndIsExtendedInPlace() throws Exception {
        Memory file = new Memory(FTYP, MDAT, MOOV);

        assertTrue(Mp4Tags.apply(file, tags()));

        assertEquals(Arrays.asList("ftyp", "mdat", "moov"), topLevel(file.data));
        assertMdatUntouched(file.data, FTYP.length);
        assertTagged(boxAt(file.data, FTYP.length + MDAT.length));
    }

    @Test
    public void aMoovWithNoRoomIsMovedToTheEnd() throws Exception {
        Memory file = new Memory(FTYP, MOOV, Mp4Tags.box("free", new byte[4]), MDAT);
        long mdatAt = FTYP.length + MOOV.length + 12;

        assertTrue(Mp4Tags.apply(file, tags()));

        assertEquals(Arrays.asList("ftyp", "free", "free", "mdat", "moov"), topLevel(file.data));
        assertMdatUntouched(file.data, mdatAt);
        assertTagged(boxAt(file.data, mdatAt + MDAT.length));
    }

    @Test
    public void aFreeBoxOfExactlyTheRightSizeIsConsumed() throws Exception {
        byte[] udta = Mp4Tags.buildUdta(tags());
        Memory file = new Memory(FTYP, MOOV, Mp4Tags.box("free", new byte[udta.length - 8]), MDAT);

        assertTrue(Mp4Tags.apply(file, tags()));

        assertEquals(Arrays.asList("ftyp", "moov", "mdat"), topLevel(file.data));
        assertTagged(boxAt(file.data, FTYP.length));
    }

    @Test
    public void anAlreadyTaggedFileIsLeftAlone() throws Exception {
        Memory file = new Memory(FTYP, MDAT, MOOV);
        assertTrue(Mp4Tags.apply(file, tags()));
        byte[] once = file.data.clone();

        assertFalse(Mp4Tags.apply(file, tags()));
        assertArrayEquals(once, file.data);
    }

    @Test
    public void aBoxRunningToTheEndOfTheFileStopsTagging() throws Exception {
        byte[] openMdat = MDAT.clone();
        openMdat[0] = openMdat[1] = openMdat[2] = openMdat[3] = 0;
        Memory file = new Memory(FTYP, MOOV, openMdat);
        byte[] before = file.data.clone();

        assertFalse(Mp4Tags.apply(file, tags()));
        assertArrayEquals(before, file.data);
    }

    @Test
    public void nothingToWriteLeavesTheFileAlone() throws Exception {
        Memory file = new Memory(FTYP, MDAT, MOOV);
        byte[] before = file.data.clone();

        assertFalse(Mp4Tags.apply(file, new Mp4Tags.Tags()));
        assertArrayEquals(before, file.data);
    }

    @Test
    public void anUnknownPictureFormatIsNotEmbedded() {
        Mp4Tags.Tags tags = new Mp4Tags.Tags();
        tags.title = "t";
        tags.cover = new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

        assertFalse(contains(Mp4Tags.buildUdta(tags), ascii("covr")));
    }

    private static Mp4Tags.Tags tags() {
        Mp4Tags.Tags tags = new Mp4Tags.Tags();
        tags.title = "Título — 日本";
        tags.artist = "Channel";
        tags.comment = "https://youtu.be/abc";
        tags.cover = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};
        return tags;
    }

    private static void assertTagged(byte[] moov) {
        assertEquals("moov", type(moov, 0));
        assertEquals(moov.length, Mp4Tags.u32(moov, 0));
        assertTrue(Mp4Tags.hasChild(moov, "udta"));
        assertTrue(contains(moov, "Título — 日本".getBytes(StandardCharsets.UTF_8)));
        assertTrue(contains(moov, new byte[]{(byte) 0xA9, 'n', 'a', 'm'}));
        assertTrue(contains(moov, new byte[]{(byte) 0xA9, 'A', 'R', 'T'}));
        assertTrue(contains(moov, ascii("covr")));
        assertTrue(contains(moov, ascii("mdirappl")));
    }

    private static void assertMdatUntouched(byte[] file, long at) {
        assertArrayEquals(MDAT, Arrays.copyOfRange(file, (int) at, (int) at + MDAT.length));
    }

    private static byte[] boxAt(byte[] file, long at) {
        int size = (int) Mp4Tags.u32(file, (int) at);
        return Arrays.copyOfRange(file, (int) at, (int) at + size);
    }

    private static List<String> topLevel(byte[] file) {
        List<String> types = new ArrayList<>();
        int pos = 0;
        while (pos + 8 <= file.length) {
            long size = Mp4Tags.u32(file, pos);
            types.add(type(file, pos));
            assertTrue("box size at " + pos, size >= 8 && pos + size <= file.length);
            pos += (int) size;
        }
        assertEquals("boxes cover the whole file", file.length, pos);
        return types;
    }

    private static String type(byte[] data, int pos) {
        return new String(data, pos + 4, 4, StandardCharsets.ISO_8859_1);
    }

    private static boolean contains(byte[] data, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= data.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] filled(int length, byte value) {
        byte[] out = new byte[length];
        Arrays.fill(out, value);
        return out;
    }

    private static final class Memory implements Mp4Tags.RandomAccess {
        byte[] data;

        Memory(byte[]... boxes) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte[] box : boxes) {
                out.write(box, 0, box.length);
            }
            data = out.toByteArray();
        }

        @Override
        public long length() {
            return data.length;
        }

        @Override
        public void read(long position, byte[] dst, int offset, int length) {
            System.arraycopy(data, (int) position, dst, offset, length);
        }

        @Override
        public void write(long position, byte[] src, int offset, int length) {
            int end = (int) position + length;
            if (end > data.length) {
                data = Arrays.copyOf(data, end);
            }
            System.arraycopy(src, offset, data, (int) position, length);
        }
    }
}
