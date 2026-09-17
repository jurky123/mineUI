package com.mineui.ui.util;

import java.io.IOException;
import java.io.InputStream;

/** 读取 PNG 头部尺寸（纯 Java，便于单测）。 */
public final class PngDimensions {

    /** PNG 签名 + IHDR 头最小长度：8 签名 + 4 长度 + 4 类型 + 4 宽 + 4 高。 */
    private static final int HEADER_BYTES = 24;

    private PngDimensions() {
    }

    /**
     * @return {@code [width, height]}；非 PNG 或读取失败返回 null
     */
    public static int[] read(InputStream in) {
        if (in == null) {
            return null;
        }
        try {
            byte[] header = in.readNBytes(HEADER_BYTES);
            if (header.length < HEADER_BYTES) {
                return null;
            }
            byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
            for (int i = 0; i < signature.length; i++) {
                if (header[i] != signature[i]) {
                    return null;
                }
            }
            if (header[12] != 'I' || header[13] != 'H' || header[14] != 'D' || header[15] != 'R') {
                return null;
            }
            int width = readInt(header, 16);
            int height = readInt(header, 20);
            if (width <= 0 || height <= 0) {
                return null;
            }
            return new int[]{width, height};
        } catch (IOException e) {
            return null;
        }
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }
}
