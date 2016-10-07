package com.noxmedical.blescanner.models;

import android.support.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class BleAdvertiseSegment {
    public final int segmentType;
    public final byte[] segmentData;
    public BleAdvertiseSegment(int segmentType, byte[] segmentData) {
        this.segmentType = segmentType;
        this.segmentData = segmentData;
    }

    /**
     * Breaks a Bluetooth Low Energy advertisement into segments. Assumes there is no message length specifier (as is
     * the behavior of the getBytes() method for a ScanRecord)
     */
    public static @NonNull BleAdvertiseSegment[] segmentize(@NonNull byte[] bleMessage) throws InvalidMessageException {
        ByteBuffer bb = ByteBuffer.wrap(bleMessage);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        List<BleAdvertiseSegment> out = new ArrayList<>();
        while(bb.remaining() > 2) { // need at least 2 bytes for segment length + type
            final int segmentLength = (bb.get() & 0xFF);
            if(segmentLength == 0) {
                break;
            }
            if(bb.remaining() < segmentLength) {
                throw new InvalidMessageException("Message contained fewer byte that indicated by segment length");
            }
            final int segmentType = (bb.get() & 0xFF);
            final byte[] data = new byte[segmentLength - 1]; //exclude segment type
            bb.get(data);
            out.add(new BleAdvertiseSegment(segmentType, data));
        }
        BleAdvertiseSegment[] outArray = new BleAdvertiseSegment[out.size()];
        out.toArray(outArray);
        return outArray;
    }

    public static class InvalidMessageException extends Throwable {
        public InvalidMessageException(String msg) {
            super(msg);
        }
    }
}
