package com.arter97.streamer;

import java.nio.ByteBuffer;

import static org.jcodec.codecs.h264.H264Utils.gotoNALUnit;
import static org.jcodec.codecs.h264.H264Utils.gotoNALUnitWithArray;

public class LocalH264Utils {
    public static ByteBuffer nextNALUnit(ByteBuffer buf) {
        skipToNALUnit(buf);

        if (buf.hasArray())
            return gotoNALUnitWithArray(buf);
        else
            return gotoNALUnit(buf);
    }

    public static final void skipToNALUnit(ByteBuffer buf) {
        if (!buf.hasRemaining())
            return;

        int val = 0xffffffff;
        while (buf.hasRemaining()) {
            val <<= 8;
            val |= (buf.get() & 0xff);
            if ((val & 0xffffff) == 1) {
                buf.position(buf.position());
                break;
            }
        }
    }
}
