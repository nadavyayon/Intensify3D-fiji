package com.mycompany.imagej;

import ij.process.ImageProcessor;
import ij.process.FloatProcessor;

public class SavitzkyGolay2D {

    public static void applyFilter(ImageProcessor ip, int windowSize) {
        if (windowSize % 2 == 0) {
            throw new IllegalArgumentException("Window size must be odd.");
        }

        int width = ip.getWidth();
        int height = ip.getHeight();
        int halfWindow = windowSize / 2;

        float[][] sgKernel = generateCircularKernel(windowSize);

        FloatProcessor fp = ip.convertToFloatProcessor();

        double origMin = fp.getMin();
        double origMax = fp.getMax();

        // *** KEY CHANGE: Add a small offset BEFORE normalization ***
        double offset = (origMax - origMin) * 0.001; // 0.1% of the original range
        if (offset == 0) offset = 1e-6;     // If image is flat, avoid zero offset

        // Normalize to 0-1 range, but with the offset
        if (origMax > origMin) {
            fp.add(offset);         // Add the offset to ALL pixels
            fp.subtract(origMin);  // Now subtract the original minimum
            fp.multiply(1.0 / (origMax - origMin + offset)); // Divide by (range + offset)
        }

        FloatProcessor paddedFp = padImage(fp, halfWindow);
        float[][] filteredPixels = convolve(paddedFp, sgKernel, windowSize);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                fp.putPixelValue(x, y, filteredPixels[y][x]);
            }
        }

        // Restore original intensity scale, accounting for the offset
        fp.multiply(origMax - origMin + offset);
        fp.add(origMin);
        fp.subtract(offset); // Subtract the offset we added earlier

        ip.setPixels(fp.getPixels());
    }
    // ... (rest of your class, including generateCircularKernel, convolve, and padImage) ...
    // The padding and convolution are still correct, no changes needed there.
    private static float[][] generateCircularKernel(int windowSize) {
        int halfWindow = windowSize / 2;
        float[][] kernel = new float[windowSize][windowSize];

        double sigma = windowSize / 3.0; // Standard deviation for weighting (adjust as needed)
        double sum = 0;

        // Generate circular kernel with Gaussian-like weighting
        for (int y = -halfWindow; y <= halfWindow; y++) {
            for (int x = -halfWindow; x <= halfWindow; x++) {
                double distance = Math.sqrt(x * x + y * y);

                if (distance <= halfWindow) { // Inside circular region
                    //Gaussian-like weighting
                    kernel[y + halfWindow][x + halfWindow] = (float) Math.exp(-(distance * distance) / (2.0 * sigma * sigma));
                    sum += kernel[y + halfWindow][x + halfWindow];
                } else {
                    kernel[y + halfWindow][x + halfWindow] = 0; // Outside circular area: set to 0
                }
            }
        }

        // Normalize kernel so sum = 1
        for (int y = 0; y < windowSize; y++) {
            for (int x = 0; x < windowSize; x++) {
                kernel[y][x] /= sum;
            }
        }

        return kernel;
    }

    /**
     * Convolves an image with a circular Savitzky-Golay kernel.
     *
     * @param ip         Padded FloatProcessor of the image
     * @param kernel     2D kernel array
     * @param windowSize Kernel size (odd number)
     * @return The smoothed pixel values as a 2D array, *without* the padding.
     */
    private static float[][] convolve(FloatProcessor ip, float[][] kernel, int windowSize) {
        int width = ip.getWidth();
        int height = ip.getHeight();
        int halfWindow = windowSize / 2;
        // The result array should be the size of the *original* image (no padding)
        float[][] result = new float[height - 2 * halfWindow][width - 2 * halfWindow];

        // Iterate over the *original* image dimensions (excluding padding)
        for (int y = halfWindow; y < height - halfWindow; y++) {
            for (int x = halfWindow; x < width - halfWindow; x++) {
                float sum = 0;
                // Apply kernel centered at (x, y) in the *padded* image
                for (int ky = -halfWindow; ky <= halfWindow; ky++) {
                    for (int kx = -halfWindow; kx <= halfWindow; kx++) {
                        sum += ip.getPixelValue(x + kx, y + ky) * kernel[ky + halfWindow][kx + halfWindow];
                    }
                }
                // Store the result in the corresponding location in the *unpadded* result array
                result[y - halfWindow][x - halfWindow] = sum;
            }
        }
        return result;
    }



    /**
     * Pads the image by mirroring edges to prevent edge artifacts during convolution.
     * This version CORRECTLY handles corner mirroring.
     *
     * @param ip  Original FloatProcessor
     * @param pad Padding size (half the window size)
     * @return Padded FloatProcessor
     */
    private static FloatProcessor padImage(FloatProcessor ip, int pad) {
        int w = ip.getWidth();
        int h = ip.getHeight();
        int newW = w + 2 * pad;  // Add padding on both sides
        int newH = h + 2 * pad;

        FloatProcessor paddedIp = new FloatProcessor(newW, newH);

        // 1. Copy the original image into the center of the padded image
        paddedIp.insert(ip, pad, pad);

        // 2. Mirror the edges

        // Top and Bottom edges
        for (int x = pad; x < w + pad; x++) { // Iterate over the *padded* width
            for (int i = 0; i < pad; i++) {
                // Top edge: reflect rows above the image
                paddedIp.putPixelValue(x, i, ip.getPixelValue(x - pad, pad - 1 - i));
                // Bottom edge: reflect rows below the image
                paddedIp.putPixelValue(x, newH - 1 - i, ip.getPixelValue(x - pad, h - 1 - (pad - 1 - i)));
            }
        }

        // Left and Right edges
        for (int y = pad; y < h + pad; y++) { // Iterate over the *padded* height
            for (int i = 0; i < pad; i++) {
                // Left edge: reflect columns to the left
                paddedIp.putPixelValue(i, y, ip.getPixelValue(pad - 1 - i, y - pad));
                // Right edge: reflect columns to the right
                paddedIp.putPixelValue(newW - 1 - i, y, ip.getPixelValue(w - 1 - (pad - 1 - i), y - pad));
            }
        }

        // 3. **CORRECTED Corner Mirroring:**  Mirror the corner *pixels*, not across the padding boundary.

        for (int y = 0; y < pad; y++) {
            for (int x = 0; x < pad; x++) {
                // Top-left corner:  Reflect the top-left corner pixel of the *original* image.
                paddedIp.putPixelValue(x, y, ip.getPixelValue(pad-1-x, pad-1-y));

                // Top-right corner: Reflect the top-right corner pixel of the *original* image.
                paddedIp.putPixelValue(newW - 1 - x, y, ip.getPixelValue(w - 1 - (pad - 1 - x), pad - 1 - y));

                // Bottom-left corner: Reflect the bottom-left corner pixel of the *original* image.
                paddedIp.putPixelValue(x, newH - 1 - y, ip.getPixelValue(pad - 1 - x, h - 1 - (pad - 1 - y)));

                // Bottom-right corner: Reflect the bottom-right corner pixel of the *original* image.
                paddedIp.putPixelValue(newW - 1 - x, newH - 1 - y, ip.getPixelValue(w - 1 - (pad - 1 - x), h - 1 - (pad - 1 - y)));
            }
        }

        return paddedIp;
    }
}