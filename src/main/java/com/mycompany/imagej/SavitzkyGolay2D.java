package com.mycompany.imagej;

import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

public class SavitzkyGolay2D {

    public static void applyFilter(ImageProcessor ip, int windowSize) {
        if (windowSize % 2 == 0) {
            throw new IllegalArgumentException("Window size must be odd.");
        }

        int width = ip.getWidth();
        int height = ip.getHeight();
        int halfWindow = windowSize / 2;

        // Generate the true 2D Savitzky-Golay kernel (order 2 polynomial)
        float[][] sgKernel = generateSavitzkyGolayKernel(windowSize);

        FloatProcessor fp = ip.convertToFloatProcessor();

        // Normalize before filtering
        double origMin = fp.getMin();
        double origMax = fp.getMax();
        double offset = (origMax - origMin) * 0.001; // Small offset for stability
        if (offset == 0) offset = 1e-6;

        fp.add(offset);
        fp.subtract(origMin);
        fp.multiply(1.0 / (origMax - origMin + offset));

        FloatProcessor paddedFp = padImage(fp, halfWindow);
        float[][] filteredPixels = convolve(paddedFp, sgKernel, windowSize);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                fp.putPixelValue(x, y, filteredPixels[y][x]);
            }
        }

        // Restore intensity scale
        fp.multiply(origMax - origMin + offset);
        fp.add(origMin);
        fp.subtract(offset);

        ip.setPixels(fp.getPixels());
    }

    /**
     * Generates a true 2D Savitzky-Golay filter kernel of order 2.
     * Uses a least-squares polynomial fitting.
     *
     * @param windowSize Size of the window (must be odd)
     * @return 2D filter kernel
     */
    private static float[][] generateSavitzkyGolayKernel(int windowSize) {
        int halfWindow = windowSize / 2;
        float[][] kernel = new float[windowSize][windowSize];

        double[][] A = new double[windowSize * windowSize][6]; // Quadratic terms
        double[] b = new double[windowSize * windowSize];

        int index = 0;
        for (int y = -halfWindow; y <= halfWindow; y++) {
            for (int x = -halfWindow; x <= halfWindow; x++) {
                A[index][0] = 1;       // Constant term
                A[index][1] = x;       // x
                A[index][2] = y;       // y
                A[index][3] = x * x;   // x^2
                A[index][4] = x * y;   // xy
                A[index][5] = y * y;   // y^2

                b[index] = (x == 0 && y == 0) ? 1 : 0; // Preserve center weight
                index++;
            }
        }

        double[] coeffs = solveLeastSquares(A, b);
        index = 0;

        for (int y = -halfWindow; y <= halfWindow; y++) {
            for (int x = -halfWindow; x <= halfWindow; x++) {
                kernel[y + halfWindow][x + halfWindow] =
                        (float) (coeffs[0] + coeffs[1] * x + coeffs[2] * y +
                                coeffs[3] * x * x + coeffs[4] * x * y + coeffs[5] * y * y);
            }
        }

        return kernel;
    }

    /**
     * Solves Ax = b using least squares for polynomial fitting.
     * Uses normal equations (A^T A)x = A^T b.
     */
    private static double[] solveLeastSquares(double[][] A, double[] b) {
        int rows = A.length, cols = A[0].length;
        double[][] AtA = new double[cols][cols];
        double[] Atb = new double[cols];

        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < cols; j++) {
                for (int k = 0; k < rows; k++) {
                    AtA[i][j] += A[k][i] * A[k][j];
                }
            }
            for (int k = 0; k < rows; k++) {
                Atb[i] += A[k][i] * b[k];
            }
        }

        return gaussianElimination(AtA, Atb);
    }

    private static double[] gaussianElimination(double[][] A, double[] b) {
        int n = b.length;
        double[] x = new double[n];

        for (int i = 0; i < n; i++) {
            int max = i;
            for (int j = i + 1; j < n; j++) {
                if (Math.abs(A[j][i]) > Math.abs(A[max][i])) max = j;
            }

            double[] temp = A[i]; A[i] = A[max]; A[max] = temp;
            double t = b[i]; b[i] = b[max]; b[max] = t;

            for (int j = i + 1; j < n; j++) {
                double factor = A[j][i] / A[i][i];
                for (int k = i; k < n; k++) A[j][k] -= factor * A[i][k];
                b[j] -= factor * b[i];
            }
        }

        for (int i = n - 1; i >= 0; i--) {
            double sum = 0;
            for (int j = i + 1; j < n; j++) sum += A[i][j] * x[j];
            x[i] = (b[i] - sum) / A[i][i];
        }

        return x;
    }

    private static float[][] convolve(FloatProcessor ip, float[][] kernel, int windowSize) {
        int width = ip.getWidth();
        int height = ip.getHeight();
        int halfWindow = windowSize / 2;
        float[][] result = new float[height - 2 * halfWindow][width - 2 * halfWindow];

        for (int y = halfWindow; y < height - halfWindow; y++) {
            for (int x = halfWindow; x < width - halfWindow; x++) {
                float sum = 0;
                for (int ky = -halfWindow; ky <= halfWindow; ky++) {
                    for (int kx = -halfWindow; kx <= halfWindow; kx++) {
                        sum += ip.getPixelValue(x + kx, y + ky) * kernel[ky + halfWindow][kx + halfWindow];
                    }
                }
                result[y - halfWindow][x - halfWindow] = sum;
            }
        }
        return result;
    }

    private static FloatProcessor padImage(FloatProcessor ip, int pad) {
        int w = ip.getWidth();
        int h = ip.getHeight();
        int newW = w + 2 * pad;
        int newH = h + 2 * pad;

        FloatProcessor paddedIp = new FloatProcessor(newW, newH);

        // 1️⃣ Copy Original Image into the Center of the Padded Image
        paddedIp.insert(ip, pad, pad);

        // 2️⃣ Mirror Top and Bottom Edges
        for (int x = pad; x < w + pad; x++) {
            for (int i = 0; i < pad; i++) {
                // Mirror Top Edge
                paddedIp.putPixelValue(x, i, ip.getPixelValue(x - pad, pad - i));

                // Mirror Bottom Edge
                paddedIp.putPixelValue(x, newH - 1 - i, ip.getPixelValue(x - pad, h - 1 - (pad - i)));
            }
        }

        // 3️⃣ Mirror Left and Right Edges
        for (int y = pad; y < h + pad; y++) {
            for (int i = 0; i < pad; i++) {
                // Mirror Left Edge
                paddedIp.putPixelValue(i, y, ip.getPixelValue(pad - i, y - pad));

                // Mirror Right Edge
                paddedIp.putPixelValue(newW - 1 - i, y, ip.getPixelValue(w - 1 - (pad - i), y - pad));
            }
        }

        // 4️⃣ Correctly Handle Corner Mirroring
        for (int y = 0; y < pad; y++) {
            for (int x = 0; x < pad; x++) {
                // Top-Left Corner
                paddedIp.putPixelValue(x, y, ip.getPixelValue(pad - x, pad - y));

                // Top-Right Corner
                paddedIp.putPixelValue(newW - 1 - x, y, ip.getPixelValue(w - 1 - (pad - x), pad - y));

                // Bottom-Left Corner
                paddedIp.putPixelValue(x, newH - 1 - y, ip.getPixelValue(pad - x, h - 1 - (pad - y)));

                // Bottom-Right Corner
                paddedIp.putPixelValue(newW - 1 - x, newH - 1 - y, ip.getPixelValue(w - 1 - (pad - x), h - 1 - (pad - y)));
            }
        }

        return paddedIp;
    }
}