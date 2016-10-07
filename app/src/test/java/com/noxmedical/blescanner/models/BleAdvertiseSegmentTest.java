package com.noxmedical.blescanner.models;

import android.util.Base64;

import com.noxmedical.blescanner.BuildConfig;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.ByteBuffer;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class BleAdvertiseSegmentTest {
    /** Contains 2 Nox Manufacturer specific messages but not message length byte, i.e.:
     * '\x14\xff\xfb\x03\x00\xcd\xab\x00\x11"3DUfoobar\xc3\xb0\x11\xff\xfb\x03\x01\x071234567\x04abcd' */
    private final static String B64_BLE_MSG = "FP/7AwDNqwARIjNEVWZvb2JhcsOwEf/7AwEHMTIzNDU2NwRhYmNk";

    @Test
    public void testSegmentizeData() throws BleAdvertiseSegment.InvalidMessageException {
        byte[] msg = Base64.decode(B64_BLE_MSG, Base64.DEFAULT);
        BleAdvertiseSegment[] segments = BleAdvertiseSegment.segmentize(msg);
        assertEquals(2, segments.length);
    }

    @Test
    public void testSegmentizeBadData() {
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.put((byte)4); // set length as 4, which is invalid
        boolean hasError = false;
        try {
            BleAdvertiseSegment.segmentize(bb.array());
        } catch (BleAdvertiseSegment.InvalidMessageException e) {
            hasError = true;
            e.printStackTrace();
        }
        assertTrue(hasError);

    }
}