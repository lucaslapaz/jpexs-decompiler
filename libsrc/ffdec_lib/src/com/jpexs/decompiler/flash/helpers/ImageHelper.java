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

import com.jpexs.decompiler.flash.SWF;
import com.jpexs.decompiler.flash.tags.enums.ImageFormat;
import com.jpexs.decompiler.flash.types.RGBA;
import com.jpexs.helpers.Helper;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.monte.media.jpeg.CMYKJPEGImageReader;
import org.monte.media.jpeg.CMYKJPEGImageReaderSpi;

/**
 * Image helper.
 *
 * @author JPEXS
 */
public class ImageHelper {

    static {
        ImageIO.setUseCache(false);
    }

    /**
     * Reads image from byte array.
     * @param data Image data
     * @return Image
     */
    public static BufferedImage read(byte[] data) throws IOException {
        return read(new ByteArrayInputStream(data));
    }

    /**
     * Reads image from input stream.
     * @param input Input stream
     * @return Image
     * @throws IOException On I/O error
     */
    public static BufferedImage read(InputStream input) throws IOException {
        BufferedImage in;
        byte[] data = Helper.readStream(input);
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            CMYKJPEGImageReader r = new CMYKJPEGImageReader(new CMYKJPEGImageReaderSpi());
            r.setInput(iis);
            in = r.read(0);
        } catch (IOException | ArrayIndexOutOfBoundsException ex) {
            try {
                in = ImageIO.read(ImageIO.createImageInputStream(new ByteArrayInputStream(data)));
            } catch (IOException ex1) {
                return null;
            }
        }

        if (in == null) {
            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics g = img.getGraphics();
            g.setColor(SWF.ERROR_COLOR);
            g.fillRect(0, 0, 1, 1);
            return img;
        }

        int type = in.getType();
        if (type != BufferedImage.TYPE_INT_ARGB_PRE && type != BufferedImage.TYPE_INT_RGB) {
            // convert to ARGB
            int width = in.getWidth();
            int height = in.getHeight();
            int[] imgData = in.getRGB(0, 0, width, height, null, 0, width);
            BufferedImage newImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
            newImage.getRaster().setDataElements(0, 0, width, height, imgData);
            return newImage;
        }

        return in;
    }

    /**
     * Writes image to file.
     * @param image Image
     * @param format Image format
     * @param output Output file
     * @throws IOException On I/O error
     */
    public static void write(BufferedImage image, ImageFormat format, File output) throws IOException {
        String formatName = getImageFormatString(format).toUpperCase(Locale.ENGLISH);
        if (format == ImageFormat.JPEG) {
            image = fixImageIOJpegBug(image);
        } else {
            image = unmultiplyAlpha(image);
        }

        ImageIO.write(image, formatName, output);
    }

    /**
     * Writes image to output stream.
     * @param image Image
     * @param format Image format
     * @param output Output stream
     * @throws IOException On I/O error
     */
    public static void write(BufferedImage image, ImageFormat format, OutputStream output) throws IOException {
        String formatName = getImageFormatString(format).toUpperCase(Locale.ENGLISH);
        if (format == ImageFormat.JPEG) {
            image = fixImageIOJpegBug(image);
        } else {
            image = unmultiplyAlpha(image);
        }

        ImageIO.write(image, formatName, output);
    }

    /**
     * Writes image to byte array.
     * @param image Image
     * @param format Image format
     * @param output Output byte array
     */
    public static void write(BufferedImage image, ImageFormat format, ByteArrayOutputStream output) {
        String formatName = getImageFormatString(format).toUpperCase(Locale.ENGLISH);
        if (format == ImageFormat.JPEG) {
            image = fixImageIOJpegBug(image);
        } else {
            image = unmultiplyAlpha(image);
        }

        try {
            ImageIO.write(image, formatName, output);
        } catch (IOException ex) {
            Logger.getLogger(ImageHelper.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Factors that reverse the alpha premultiplication of a color component exactly like Flash Player
     * does (BitmapData.getPixel32 of a premultiplied bitmap). The table was generated by brute force
     * against Flash Player; taken from Ruffle (core/src/bitmap/bitmap_data.rs,
     * Color::to_un_multiplied_alpha, FLASH_PREMUL_FACTOR).
     */
    private static final int[] FLASH_UNMULTIPLY_FACTORS = {
        0, 16678912, 8339456, 5559638, 4169728, 3335783, 2779819, 2386603, 2086230, 1855488, 1667892, 1518251,
        1391151, 1285234, 1193302, 1111928, 1043895, 981113, 927744, 879275, 834621, 795535, 759126, 726358,
        695839, 668183, 642538, 618737, 596651, 576171, 555964, 538706, 522104, 506319, 490557, 477321,
        464038, 451353, 439544, 428244, 417582, 407500, 397768, 388535, 379630, 371117, 363179, 355235,
        348050, 340965, 334052, 327038, 321269, 315077, 309159, 303586, 298189, 293092, 287981, 283080,
        278251, 273892, 269268, 265179, 261087, 256971, 253160, 249322, 245508, 242164, 238575, 235245,
        231859, 228848, 225785, 222712, 219616, 216827, 213985, 211432, 208835, 206075, 203750, 201196,
        198895, 196223, 194301, 191987, 189686, 187636, 185559, 183426, 181453, 179444, 177638, 175855,
        174054, 171948, 170489, 168695, 166889, 165365, 163519, 162045, 160508, 158970, 157429, 156150,
        154610, 153081, 151803, 150511, 148986, 147709, 146420, 145116, 143868, 142586, 141545, 140277,
        139194, 137957, 136954, 135676, 134652, 133621, 132604, 131577, 130552, 129527, 128508, 127476,
        126451, 125432, 124670, 123645, 122818, 121847, 121082, 120060, 119288, 118263, 117502, 116720,
        115967, 115195, 114424, 113655, 112893, 112125, 111356, 110563, 109811, 109048, 108287, 107766,
        107004, 106236, 105724, 104953, 104434, 103676, 102904, 102375, 101879, 101119, 100604, 99834,
        99321, 98813, 98112, 97533, 97019, 96509, 95994, 95486, 94713, 94185, 93689, 93179,
        92667, 92149, 91643, 91129, 90621, 90068, 89597, 89342, 88829, 88318, 87804, 87294,
        87034, 86523, 85994, 85499, 85245, 84732, 84222, 83956, 83450, 82937, 82685, 82173,
        81840, 81405, 80889, 80638, 80127, 79862, 79354, 79103, 78590, 78332, 78077, 77565,
        77308, 76795, 76541, 76284, 75766, 75518, 75262, 74748, 74493, 74238, 73691, 73470,
        73214, 72959, 72447, 72189, 71935, 71671, 71166, 70911, 70651, 70399, 70140, 69886,
        69615, 69116, 68861, 68603, 68350, 68093, 67839, 67576, 67326, 67070, 66813, 66556,
        66302, 66046, 65791, 65408
    };

    /**
     * Reverses the alpha premultiplication of a color component like Flash Player.
     * @param component Premultiplied color component (0-255)
     * @param alpha Alpha (0-255)
     * @return Non-premultiplied color component (0 when alpha is 0)
     */
    public static int unmultiplyAlpha(int component, int alpha) {
        return (int) (((long) component * FLASH_UNMULTIPLY_FACTORS[alpha] + 0x8000) >>> 16) & 0xFF;
    }

    /**
     * Reverses the alpha premultiplication of an ARGB color like Flash Player.
     * @param value Premultiplied ARGB color
     * @return Non-premultiplied ARGB color
     */
    public static int unmultiplyAlpha(int value) {
        int a = (value >> 24) & 0xFF;
        int r = unmultiplyAlpha((value >> 16) & 0xFF, a);
        int g = unmultiplyAlpha((value >> 8) & 0xFF, a);
        int b = unmultiplyAlpha(value & 0xFF, a);
        return RGBA.toInt(r, g, b, a);
    }

    /**
     * Converts a premultiplied image (TYPE_INT_ARGB_PRE) to a new non-premultiplied one
     * (TYPE_INT_ARGB), reversing the premultiplication like Flash Player, so the exported pixels are
     * the ones Flash Player returns. The source image is not modified. Other images are returned as
     * they are.
     * @param image Image
     * @return Non-premultiplied image
     */
    public static BufferedImage unmultiplyAlpha(BufferedImage image) {
        if (image.getType() != BufferedImage.TYPE_INT_ARGB_PRE) {
            return image;
        }
        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        int[] resultPixels = ((DataBufferInt) result.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < pixels.length; i++) {
            resultPixels[i] = unmultiplyAlpha(pixels[i]);
        }
        return result;
    }

    private static BufferedImage fixImageIOJpegBug(BufferedImage image) {
        int type = image.getType();
        if (type != BufferedImage.TYPE_INT_RGB) {
            // convert to RGB without alpha channel
            int width = image.getWidth();
            int height = image.getHeight();
            BufferedImage newImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            int[] pixels2 = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
            for (int i = 0; i < pixels.length; i++) {
                pixels2[i] = pixels[i] & 0xffffff;
            }

            return newImage;
        }

        return image;
    }

    /**
     * Gets image format string.
     * @param format Image format
     * @return Image format string
     */
    public static String getImageFormatString(ImageFormat format) {
        switch (format) {
            case UNKNOWN:
                return "unk";
            case JPEG:
                return "jpg";
            case GIF:
                return "gif";
            case PNG:
                return "png";
            case BMP:
                return "bmp";
            case WEBP:
                return "webp";
            case JXR:
                return "jxr";
        }

        throw new Error("Unsupported image format: " + format);
    }

    /**
     * Gets image dimension.
     * @param input Input stream
     * @return Image dimension
     * @throws IOException On I/O error
     */
    public static Dimension getDimension(InputStream input) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(input)) {
            final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(in);
                    return new Dimension(reader.getWidth(0), reader.getHeight(0));
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException ex) {
            //ignored
        }

        BufferedImage image = read(input);
        return new Dimension(image.getWidth(), image.getHeight());
    }
}
