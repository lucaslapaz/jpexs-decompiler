/*
 *  Copyright (C) 2010-2026 JPEXS, All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package com.jpexs.decompiler.flash.helpers;

import com.jpexs.decompiler.flash.tags.enums.ImageFormat;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import javax.imageio.ImageIO;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;

/**
 * Exported PNG of a premultiplied image (DefineBitsLossless2, DefineBitsJPEG3 with alpha) has the
 * pixels Flash Player returns from BitmapData.getPixel32.
 */
public class ImageHelperTest {

    /**
     * Premultiplied ARGB pixels and the non-premultiplied ones Flash Player returns for them.
     */
    private static final int[][] PIXELS = {
        {0x80643201, 0x80C76402},
        {0xFF0A141E, 0xFF0A141E},
        {0x03020100, 0x03AA5500},
        {0xC8C7964D, 0xC8FDBF62},
        {0x00000000, 0x00000000},
        {0x11110904, 0x11FF873C}
    };

    private BufferedImage premultipliedImage() {
        BufferedImage image = new BufferedImage(PIXELS.length, 1, BufferedImage.TYPE_INT_ARGB_PRE);
        int[] data = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < PIXELS.length; i++) {
            data[i] = PIXELS[i][0];
        }
        return image;
    }

    private void assertFlashPixels(byte[] png) throws IOException {
        BufferedImage read = ImageIO.read(new ByteArrayInputStream(png));
        for (int i = 0; i < PIXELS.length; i++) {
            assertEquals(Integer.toHexString(read.getRGB(i, 0)), Integer.toHexString(PIXELS[i][1]), "pixel " + i);
        }
    }

    @Test
    public void testUnmultiplyAlphaLikeFlashPlayer() {
        for (int[] pixel : PIXELS) {
            assertEquals(Integer.toHexString(ImageHelper.unmultiplyAlpha(pixel[0])), Integer.toHexString(pixel[1]));
        }
    }

    @Test
    public void testPngToByteArrayHasFlashPixelsAndKeepsSourceImage() throws IOException {
        BufferedImage image = premultipliedImage();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageHelper.write(image, ImageFormat.PNG, output);
        assertFlashPixels(output.toByteArray());
        int[] data = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < PIXELS.length; i++) {
            assertEquals(Integer.toHexString(data[i]), Integer.toHexString(PIXELS[i][0]), "source pixel " + i);
        }
    }

    @Test
    public void testPngToStreamHasFlashPixels() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageHelper.write(premultipliedImage(), ImageFormat.PNG, (OutputStream) output);
        assertFlashPixels(output.toByteArray());
    }
}
