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
package com.jpexs.decompiler.flash.tags;

import com.jpexs.decompiler.flash.SWF;
import com.jpexs.decompiler.flash.helpers.ImageHelper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;

/**
 * Pixels of a DefineBitsJPEG3 with alpha are the ones Flash Player returns from
 * BitmapData.getPixel32: the premultiplied JPEG colors are clamped to the alpha value and the
 * premultiplication is reversed like Flash Player.
 */
public class DefineBitsJPEG3TagTest {

    @Test
    public void testColorsAreClampedToAlphaAndUnmultipliedLikeFlashPlayer() throws IOException {
        int width = 8;
        BufferedImage source = new BufferedImage(width, 8, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < width; x++) {
                source.setRGB(x, y, 0xC86432);
            }
        }
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(source, "JPEG", jpeg);
        // the decoded JPEG color (lossy), as the tag reads it
        int decoded = ImageIO.read(new ByteArrayInputStream(jpeg.toByteArray())).getRGB(0, 0);

        DefineBitsJPEG3Tag tag = new DefineBitsJPEG3Tag(new SWF());
        tag.setImage(jpeg.toByteArray());
        byte[] alpha = new byte[width * 8];
        int[] alphas = {255, 128, 3, 0, 100, 255, 1, 200};
        for (int i = 0; i < alpha.length; i++) {
            alpha[i] = (byte) alphas[i % width];
        }
        tag.setImageAlpha(alpha);

        BufferedImage image = tag.getImageCached().getBufferedImage();
        for (int x = 0; x < width; x++) {
            int a = alphas[x];
            int expected = (a << 24)
                    | (ImageHelper.unmultiplyAlpha(Math.min((decoded >> 16) & 0xFF, a), a) << 16)
                    | (ImageHelper.unmultiplyAlpha(Math.min((decoded >> 8) & 0xFF, a), a) << 8)
                    | ImageHelper.unmultiplyAlpha(Math.min(decoded & 0xFF, a), a);
            assertEquals(Integer.toHexString(image.getRGB(x, 0)), Integer.toHexString(expected), "pixel " + x);
        }
        // a color above a low alpha is clamped to it: fully saturated, not wrapped around
        assertEquals(Integer.toHexString(image.getRGB(2, 0)), "3ffffff");
        assertEquals(Integer.toHexString(image.getRGB(6, 0)), "1ffffff");
        // fully transparent: no color
        assertEquals(image.getRGB(3, 0), 0);
    }
}
