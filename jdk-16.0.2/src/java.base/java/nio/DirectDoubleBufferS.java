/*
 * Copyright (c) 2000, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

// -- This file was mechanically generated: Do not edit! -- //

package java.nio;

import java.io.FileDescriptor;
import java.lang.ref.Reference;
import java.util.Objects;
import jdk.internal.access.foreign.MemorySegmentProxy;
import jdk.internal.misc.ScopedMemoryAccess.Scope;
import jdk.internal.misc.VM;
import jdk.internal.ref.Cleaner;
import sun.nio.ch.DirectBuffer;


class DirectDoubleBufferS

    extends DoubleBuffer



    implements DirectBuffer
{



    // Cached array base offset
    private static final long ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(double[].class);

    // Cached unaligned-access capability
    protected static final boolean UNALIGNED = Bits.unaligned();

    // Base address, used in all indexing calculations
    // NOTE: moved up to Buffer.java for speed in JNI GetDirectBufferAddress
    //    protected long address;

    // An object attached to this buffer. If this buffer is a view of another
    // buffer then we use this field to keep a reference to that buffer to
    // ensure that its memory isn't freed before we are done with it.
    private final Object att;

    public Object attachment() {
        return att;
    }




































    public Cleaner cleaner() { return null; }




























































































    // For duplicates and slices
    //
    DirectDoubleBufferS(DirectBuffer db,         // package-private
                               int mark, int pos, int lim, int cap,
                               int off, MemorySegmentProxy segment)
    {

        super(mark, pos, lim, cap, segment);
        address = ((Buffer)db).address + off;



        Object attachment = db.attachment();
        att = (attachment == null ? db : attachment);




    }

    @Override
    Object base() {
        return null;
    }

    public DoubleBuffer slice() {
        int pos = this.position();
        int lim = this.limit();
        int rem = (pos <= lim ? lim - pos : 0);
        int off = (pos << 3);
        assert (off >= 0);
        return new DirectDoubleBufferS(this, -1, 0, rem, rem, off, segment);
    }

    @Override
    public DoubleBuffer slice(int index, int length) {
        Objects.checkFromIndexSize(index, length, limit());
        return new DirectDoubleBufferS(this,
                                              -1,
                                              0,
                                              length,
                                              length,
                                              index << 3,
                                              segment);
    }

    public DoubleBuffer duplicate() {
        return new DirectDoubleBufferS(this,
                                              this.markValue(),
                                              this.position(),
                                              this.limit(),
                                              this.capacity(),
                                              0, segment);
    }

    public DoubleBuffer asReadOnlyBuffer() {

        return new DirectDoubleBufferRS(this,
                                           this.markValue(),
                                           this.position(),
                                           this.limit(),
                                           this.capacity(),
                                           0, segment);



    }



    public long address() {
        Scope scope = scope();
        if (scope != null) {
            if (scope.ownerThread() == null) {
                throw new UnsupportedOperationException("ByteBuffer derived from shared segments not supported");
            }
            try {
                scope.checkValidState();
            } catch (Scope.ScopedAccessError e) {
                throw new IllegalStateException("This segment is already closed");
            }
        }
        return address;
    }

    private long ix(int i) {
        return address + ((long)i << 3);
    }

    public double get() {
        try {
            return Double.longBitsToDouble(Bits.swap(SCOPED_MEMORY_ACCESS.getLong(scope(), null, ix(nextGetIndex()))));
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    public double get(int i) {
        try {
            return Double.longBitsToDouble(Bits.swap(SCOPED_MEMORY_ACCESS.getLong(scope(), null, ix(checkIndex(i)))));
        } finally {
            Reference.reachabilityFence(this);
        }
    }











    public DoubleBuffer get(double[] dst, int offset, int length) {

        if (((long)length << 3) > Bits.JNI_COPY_TO_ARRAY_THRESHOLD) {
            Objects.checkFromIndexSize(offset, length, dst.length);
            int pos = position();
            int lim = limit();
            assert (pos <= lim);
            int rem = (pos <= lim ? lim - pos : 0);
            if (length > rem)
                throw new BufferUnderflowException();

            long dstOffset = ARRAY_BASE_OFFSET + ((long)offset << 3);
            try {

                if (order() != ByteOrder.nativeOrder())
                    SCOPED_MEMORY_ACCESS.copySwapMemory(scope(), null, null,
                                          ix(pos),
                                          dst,
                                          dstOffset,
                                          (long)length << 3,
                                          (long)1 << 3);
                else

                    SCOPED_MEMORY_ACCESS.copyMemory(scope(), null, null,
                                      ix(pos),
                                      dst,
                                      dstOffset,
                                      (long)length << 3);
            } finally {
                Reference.reachabilityFence(this);
            }
            position(pos + length);
        } else {
            super.get(dst, offset, length);
        }
        return this;



    }

    public DoubleBuffer get(int index, double[] dst, int offset, int length) {

        if (((long)length << 3) > Bits.JNI_COPY_TO_ARRAY_THRESHOLD) {
            Objects.checkFromIndexSize(index, length, limit());
            Objects.checkFromIndexSize(offset, length, dst.length);

            long dstOffset = ARRAY_BASE_OFFSET + ((long)offset << 3);
            try {

                if (order() != ByteOrder.nativeOrder())
                    SCOPED_MEMORY_ACCESS.copySwapMemory(scope(), null, null,
                                          ix(index),
                                          dst,
                                          dstOffset,
                                          (long)length << 3,
                                          (long)1 << 3);
                else

                    SCOPED_MEMORY_ACCESS.copyMemory(scope(), null, null,
                                      ix(index),
                                      dst,
                                      dstOffset,
                                      (long)length << 3);
            } finally {
                Reference.reachabilityFence(this);
            }
        } else {
            super.get(index, dst, offset, length);
        }
        return this;



    }


    public DoubleBuffer put(double x) {

        try {
            SCOPED_MEMORY_ACCESS.putLong(scope(), null, ix(nextPutIndex()), Bits.swap(Double.doubleToRawLongBits(x)));
        } finally {
            Reference.reachabilityFence(this);
        }
        return this;



    }

    public DoubleBuffer put(int i, double x) {

        try {
            SCOPED_MEMORY_ACCESS.putLong(scope(), null, ix(checkIndex(i)), Bits.swap(Double.doubleToRawLongBits(x)));
        } finally {
            Reference.reachabilityFence(this);
        }
        return this;



    }

    public DoubleBuffer put(DoubleBuffer src) {

        super.put(src);
        return this;



    }

    public DoubleBuffer put(int index, DoubleBuffer src, int offset, int length) {

        super.put(index, src, offset, length);
        return this;



    }

    public DoubleBuffer put(double[] src, int offset, int length) {

        if (((long)length << 3) > Bits.JNI_COPY_FROM_ARRAY_THRESHOLD) {
            Objects.checkFromIndexSize(offset, length, src.length);
            int pos = position();
            int lim = limit();
            assert (pos <= lim);
            int rem = (pos <= lim ? lim - pos : 0);
            if (length > rem)
                throw new BufferOverflowException();

            long srcOffset = ARRAY_BASE_OFFSET + ((long)offset << 3);
            try {

                if (order() != ByteOrder.nativeOrder())
                    SCOPED_MEMORY_ACCESS.copySwapMemory(scope(), null, src,
                                          srcOffset,
                                          null,
                                          ix(pos),
                                          (long)length << 3,
                                          (long)1 << 3);
                else

                    SCOPED_MEMORY_ACCESS.copyMemory(scope(), null, src,
                                      srcOffset,
                                      null,
                                      ix(pos),
                                      (long)length << 3);
            } finally {
                Reference.reachabilityFence(this);
            }
            position(pos + length);
        } else {
            super.put(src, offset, length);
        }
        return this;



    }

    public DoubleBuffer put(int index, double[] src, int offset, int length) {

        if (((long)length << 3) > Bits.JNI_COPY_FROM_ARRAY_THRESHOLD) {
            Objects.checkFromIndexSize(index, length, limit());
            Objects.checkFromIndexSize(offset, length, src.length);


            long srcOffset = ARRAY_BASE_OFFSET + ((long)offset << 3);
            try {

                if (order() != ByteOrder.nativeOrder())
                    SCOPED_MEMORY_ACCESS.copySwapMemory(scope(), null, src,
                                          srcOffset,
                                          null,
                                          ix(index),
                                          (long)length << 3,
                                          (long)1 << 3);
                else

                    SCOPED_MEMORY_ACCESS.copyMemory(
                            scope(), null, src,
                            srcOffset, null, ix(index), (long)length << 3);
            } finally {
                Reference.reachabilityFence(this);
            }
        } else {
            super.put(index, src, offset, length);
        }
        return this;



    }

    public DoubleBuffer compact() {

        int pos = position();
        int lim = limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);
        try {
            SCOPED_MEMORY_ACCESS.copyMemory(scope(), null, null,
                    ix(pos), null, ix(0), (long)rem << 3);
        } finally {
            Reference.reachabilityFence(this);
        }
        position(rem);
        limit(capacity());
        discardMark();
        return this;



    }

    public boolean isDirect() {
        return true;
    }

    public boolean isReadOnly() {
        return false;
    }













































    public ByteOrder order() {

        return ((ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN)
                ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);





    }


















}
