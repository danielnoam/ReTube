package com.newtube.mobile.downloads;

import android.system.ErrnoException;
import android.system.Os;

import java.io.FileDescriptor;
import java.io.IOException;

/**
 * {@link Mp4Tags.RandomAccess} over a raw descriptor: the MediaStore and SAF outputs are only
 * reachable as an "rw" descriptor, and a {@code FileOutputStream} channel over one can't read.
 */
final class FdRandomAccess implements Mp4Tags.RandomAccess {
    private final FileDescriptor mFd;

    FdRandomAccess(FileDescriptor fd) {
        mFd = fd;
    }

    @Override
    public long length() throws IOException {
        try {
            return Os.fstat(mFd).st_size;
        } catch (ErrnoException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void read(long position, byte[] dst, int offset, int length) throws IOException {
        try {
            while (length > 0) {
                int n = Os.pread(mFd, dst, offset, length, position);
                if (n <= 0) {
                    throw new IOException("unexpected end of file");
                }
                position += n;
                offset += n;
                length -= n;
            }
        } catch (ErrnoException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void write(long position, byte[] src, int offset, int length) throws IOException {
        try {
            while (length > 0) {
                int n = Os.pwrite(mFd, src, offset, length, position);
                if (n <= 0) {
                    throw new IOException("write failed");
                }
                position += n;
                offset += n;
                length -= n;
            }
        } catch (ErrnoException e) {
            throw new IOException(e);
        }
    }
}
