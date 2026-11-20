package com.newtube.mobile.downloads;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes iTunes-style tags ({@code moov/udta/meta/ilst}: title, artist, album, comment, cover)
 * into an MP4 that {@code MediaMuxer} just finished. The muxer has no API for them, so without
 * this every file in Music/ReTube shows up as "Unknown artist" with no artwork.
 *
 * <p>The media data never moves, so no chunk offset has to be rewritten. The {@code udta} box
 * is appended to {@code moov} in one of three ways, cheapest first: into the {@code free} box
 * the muxer reserves right after a front {@code moov}; at the end of the file when {@code moov}
 * is the last box; otherwise a grown copy of {@code moov} goes to the end and the old one is
 * retyped {@code free}. In each case the box that makes the new bytes visible is written last,
 * so a process death mid-way leaves a playable (untagged) file.
 *
 * <p>Pure Java so the box layout is unit-tested on the JVM.
 */
final class Mp4Tags {
    /** Sanity bound: a moov over this is not something the muxer wrote. */
    private static final long MAX_MOOV_BYTES = 64L * 1024 * 1024;

    private static final int DATA_UTF8 = 1;
    private static final int DATA_JPEG = 13;
    private static final int DATA_PNG = 14;

    static final class Tags {
        String title;
        String artist;
        String album;
        String comment;
        /** JPEG or PNG bytes. */
        byte[] cover;
    }

    /** Positional access to the output file; reads are always for exact, in-range lengths. */
    interface RandomAccess {
        long length() throws IOException;

        void read(long position, byte[] dst, int offset, int length) throws IOException;

        void write(long position, byte[] src, int offset, int length) throws IOException;
    }

    private Mp4Tags() {
    }

    /**
     * @return true when the tags were written; false when the file's layout is not one this can
     *         safely extend (it is then left as it was)
     */
    static boolean apply(RandomAccess io, Tags tags) throws IOException {
        byte[] udta = buildUdta(tags);
        if (udta == null) {
            return false;
        }

        long fileLength = io.length();
        long moovPos = -1;
        long moovSize = 0;
        long nextPos = -1;
        long nextSize = 0;
        String nextType = null;
        byte[] header = new byte[16];
        long pos = 0;
        while (pos + 8 <= fileLength) {
            io.read(pos, header, 0, 8);
            long size = u32(header, 0);
            String type = new String(header, 4, 4, StandardCharsets.ISO_8859_1);
            int headerSize = 8;
            if (size == 1) {
                if (pos + 16 > fileLength) {
                    return false;
                }
                io.read(pos + 8, header, 8, 8);
                size = u64(header, 8);
                headerSize = 16;
            } else if (size == 0) {
                // "Extends to the end of the file": nothing may be appended after it.
                return false;
            }
            if (size < headerSize || pos + size > fileLength) {
                return false;
            }
            if (moovPos >= 0 && nextPos < 0) {
                nextPos = pos;
                nextSize = headerSize == 8 ? size : -1;
                nextType = type;
            }
            if ("moov".equals(type)) {
                if (moovPos >= 0 || headerSize != 8) {
                    return false;
                }
                moovPos = pos;
                moovSize = size;
            }
            pos += size;
        }
        if (moovPos < 0 || moovSize > MAX_MOOV_BYTES || moovSize + udta.length > 0xFFFFFFFFL) {
            return false;
        }

        byte[] moov = new byte[(int) moovSize];
        io.read(moovPos, moov, 0, moov.length);
        if (hasChild(moov, "udta")) {
            return false;
        }

        long moovEnd = moovPos + moovSize;
        byte[] grownSize = be32(moovSize + udta.length);

        boolean nextIsFree = nextPos == moovEnd && nextSize > 0
                && ("free".equals(nextType) || "skip".equals(nextType));
        if (nextIsFree && (nextSize == udta.length || nextSize >= udta.length + 8)) {
            if (nextSize > udta.length) {
                byte[] rest = box("free", new byte[0]);
                System.arraycopy(be32(nextSize - udta.length), 0, rest, 0, 4);
                io.write(moovEnd + udta.length, rest, 0, 8);
            }
            io.write(moovEnd, udta, 0, udta.length);
            io.write(moovPos, grownSize, 0, 4);
            return true;
        }

        if (moovEnd == fileLength) {
            io.write(moovEnd, udta, 0, udta.length);
            io.write(moovPos, grownSize, 0, 4);
            return true;
        }

        byte[] copy = new byte[moov.length + udta.length];
        System.arraycopy(moov, 0, copy, 0, moov.length);
        System.arraycopy(udta, 0, copy, moov.length, udta.length);
        System.arraycopy(grownSize, 0, copy, 0, 4);
        io.write(fileLength, copy, 0, copy.length);
        io.write(moovPos + 4, "free".getBytes(StandardCharsets.ISO_8859_1), 0, 4);
        return true;
    }

    /** The {@code udta} box for these tags, or null when there is nothing to write. */
    static byte[] buildUdta(Tags tags) {
        ByteArrayOutputStream items = new ByteArrayOutputStream();
        textItem(items, "©nam", tags.title);
        textItem(items, "©ART", tags.artist);
        textItem(items, "©alb", tags.album);
        textItem(items, "©cmt", tags.comment);
        if (tags.cover != null) {
            int type = isPng(tags.cover) ? DATA_PNG : isJpeg(tags.cover) ? DATA_JPEG : -1;
            if (type > 0) {
                write(items, box("covr", dataBox(type, tags.cover)));
            }
        }
        if (items.size() == 0) {
            return null;
        }

        ByteArrayOutputStream hdlr = new ByteArrayOutputStream();
        write(hdlr, new byte[8]); // version/flags, pre_defined
        write(hdlr, ascii("mdir"));
        write(hdlr, ascii("appl"));
        write(hdlr, new byte[8]); // rest of reserved
        hdlr.write(0); // empty name

        ByteArrayOutputStream meta = new ByteArrayOutputStream();
        write(meta, new byte[4]); // version/flags: meta is a full box
        write(meta, box("hdlr", hdlr.toByteArray()));
        write(meta, box("ilst", items.toByteArray()));

        return box("udta", box("meta", meta.toByteArray()));
    }

    private static void textItem(ByteArrayOutputStream out, String type, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        write(out, box(type, dataBox(DATA_UTF8, value.getBytes(StandardCharsets.UTF_8))));
    }

    private static byte[] dataBox(int type, byte[] value) {
        byte[] payload = new byte[8 + value.length];
        System.arraycopy(be32(type), 0, payload, 0, 4);
        // bytes 4..7: locale, 0
        System.arraycopy(value, 0, payload, 8, value.length);
        return box("data", payload);
    }

    static byte[] box(String type, byte[] payload) {
        byte[] out = new byte[8 + payload.length];
        System.arraycopy(be32(out.length), 0, out, 0, 4);
        System.arraycopy(ascii(type), 0, out, 4, 4);
        System.arraycopy(payload, 0, out, 8, payload.length);
        return out;
    }

    /** True when a direct child of the box (header included) has this type. */
    static boolean hasChild(byte[] box, String type) {
        int pos = 8;
        while (pos + 8 <= box.length) {
            long size = u32(box, pos);
            if (type.equals(new String(box, pos + 4, 4, StandardCharsets.ISO_8859_1))) {
                return true;
            }
            if (size < 8 || pos + size > box.length) {
                return false;
            }
            pos += (int) size;
        }
        return false;
    }

    static boolean isJpeg(byte[] data) {
        return data.length > 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF;
    }

    static boolean isPng(byte[] data) {
        return data.length > 8 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G';
    }

    /** Box types are four Latin-1 bytes; {@code ©} is 0xA9. */
    private static byte[] ascii(String type) {
        return type.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] be32(long value) {
        return new byte[]{(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value};
    }

    static long u32(byte[] b, int off) {
        return ((b[off] & 0xFFL) << 24) | ((b[off + 1] & 0xFFL) << 16) | ((b[off + 2] & 0xFFL) << 8) | (b[off + 3] & 0xFFL);
    }

    private static long u64(byte[] b, int off) {
        return (u32(b, off) << 32) | u32(b, off + 4);
    }

    private static void write(ByteArrayOutputStream out, byte[] bytes) {
        out.write(bytes, 0, bytes.length);
    }
}
