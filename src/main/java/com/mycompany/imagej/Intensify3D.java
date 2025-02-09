package com.mycompany.imagej;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.HashMap;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
//import javax.swing.*;


/**
 * Intensify3D - Image Normalization Tool
 * Now includes:
 *  ✅ Image preview
 *  ✅ Max Noise Intensity (MNI) selection
 *
 * @author Nadav Yayon
 */
public class Intensify3D {
	// GUI Components
	private JTextField stackFolderField;
	private JLabel imageCountLabel;
	private JLabel MNILabel;
	private JSpinner MNISpinner;
	private File selectedImageFile;

	// Add this field to store filter size
	private JSpinner filterSizeSpinner;

	public void showDialog() {
		JFrame frame = new JFrame("Intensify3D - Image Normalization");
		frame.setSize(600, 450);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setLayout(new GridLayout(11, 2)); // Increased row count to fit new elements

		frame.add(new JLabel("Select Image Stack Directory:"));
		JPanel pathPanel = new JPanel(new BorderLayout());
		stackFolderField = new JTextField();
		JButton browseButton = new JButton("Browse...");
		browseButton.addActionListener(this::browseForDirectory);
		pathPanel.add(stackFolderField, BorderLayout.CENTER);
		pathPanel.add(browseButton, BorderLayout.EAST);
		frame.add(pathPanel);

		frame.add(new JLabel("Images to Process:"));
		imageCountLabel = new JLabel("0");
		frame.add(imageCountLabel);

		JButton previewButton = new JButton("Preview Image");
		previewButton.addActionListener(this::previewExampleImage);
		frame.add(previewButton);
		frame.add(new JLabel(""));

		frame.add(new JLabel("Max Noise Intensity (MNI):"));
		MNISpinner = new JSpinner(new SpinnerNumberModel(50, 0, 65535, 1));
		MNISpinner.addChangeListener(e -> updateQuantile());
		frame.add(MNISpinner);

		frame.add(new JLabel("MNI Quantile (from 10000):"));
		quantileLabel = new JLabel("N/A");
		frame.add(quantileLabel);

		frame.add(new JLabel("Filter Size:"));
		filterSizeSpinner = new JSpinner(new SpinnerNumberModel(3, 3, 20, 1));
		frame.add(filterSizeSpinner);

		// ✅ Progress Bar
		frame.add(new JLabel("Progress:"));
		JProgressBar progressBar = new JProgressBar(0, 100);
		progressBar.setStringPainted(true);
		progressBar.setVisible(false);
		frame.add(progressBar);

		// ✅ Status Label
		JLabel statusLabel = new JLabel("Status: Waiting...");
		frame.add(statusLabel);

		// ✅ Execute Buttons
		JButton executeButton = new JButton("Generate Noise Images");
		executeButton.addActionListener(e -> new Thread(() -> {
			statusLabel.setText("Status: Processing Noise Images...");
			generateNoiseImages(progressBar);
			statusLabel.setText("Status: Noise Images Generated.");
		}).start());
		frame.add(executeButton);

		JButton normalizeButton = new JButton("Normalize Images");
		normalizeButton.addActionListener(e -> new Thread(() -> {
			statusLabel.setText("Status: Normalizing Images...");
			generateNormalizedImages(progressBar);
			statusLabel.setText("Status: Normalization Complete.");
		}).start());
		frame.add(normalizeButton);


		frame.setVisible(true);
	}

	private void updateFilterSizeBounds() {
		if (selectedImageFile == null) return;

		ImagePlus image = IJ.openImage(selectedImageFile.getAbsolutePath());
		if (image == null) return;

		int width = image.getWidth();
		int height = image.getHeight();
		int shortestDim = Math.min(width, height);

		int minSize = 3;
		int defaultSize = Math.max(3, shortestDim / 10);
		int maxSize = shortestDim / 2;

		// Ensure default and max filter size are odd
		if (defaultSize % 2 == 0) defaultSize++;
		if (maxSize % 2 == 0) maxSize--;

		SpinnerNumberModel model = new SpinnerNumberModel(defaultSize, minSize, maxSize, 2); // Step by 2 to keep it odd
		filterSizeSpinner.setModel(model);
	}
	private void browseForDirectory(ActionEvent e) {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		int returnValue = fileChooser.showOpenDialog(null);
		if (returnValue == JFileChooser.APPROVE_OPTION) {
			File selectedFolder = fileChooser.getSelectedFile();
			stackFolderField.setText(selectedFolder.getAbsolutePath());
			updateImageCount(selectedFolder);
		}
	}

	private void updateImageCount(File directory) {
		// Count the number of TIFF images in the folder
		File[] tiffFiles = directory.listFiles((FilenameFilter) (dir, name) -> name.toLowerCase().endsWith(".tif") || name.toLowerCase().endsWith(".tiff"));
		int count = (tiffFiles == null) ? 0 : tiffFiles.length;
		imageCountLabel.setText(String.valueOf(count));

		// Store an example image to preview
		if (count > 0) {
			selectedImageFile = tiffFiles[0];
		}
	}

	private JLabel quantileLabel; // GUI label for displaying quantile

	private void previewExampleImage(ActionEvent e) {
		if (selectedImageFile == null) {
			JOptionPane.showMessageDialog(null, "No images found in the selected folder!", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}

		ImagePlus image = IJ.openImage(selectedImageFile.getAbsolutePath());
		if (image != null) {
			image.show();
			IJ.run("Threshold..."); // Apply threshold to get default min/max values

			ImageProcessor ip = image.getProcessor();
			int[] pixelValues = samplePixelValues(ip, 10000);

			if (pixelValues.length > 0) {
				Arrays.sort(pixelValues);

				// ✅ Get the default threshold max from ImageJ (not just MAX intensity)
				double autoMaxMNI = ip.getMaxThreshold();

				// ✅ Ensure it's a valid value (ImageJ may return NaN if no threshold was set)
				if (Double.isNaN(autoMaxMNI)) {
					autoMaxMNI = ip.getMax(); // Fallback to max intensity if threshold is not set
				}

				// ✅ Update MNI Spinner with detected max threshold
				MNISpinner.setValue((int) autoMaxMNI);

				int quantileValue = findQuantile(pixelValues, (int) autoMaxMNI);
				quantileLabel.setText("MNI Quantile: " + quantileValue);
			} else {
				quantileLabel.setText("No valid pixels found.");
			}


			// ✅ Update filter size after preview image is opened
			updateFilterSizeBounds();
		} else {
			JOptionPane.showMessageDialog(null, "Failed to open the image!", "Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	private int[] samplePixelValues(ImageProcessor ip, int sampleSize) {
		Object pixels = ip.getPixels(); // Get the raw pixel array
		int totalPixels = ip.getWidth() * ip.getHeight(); // Total pixels

		int[] sampledValues = new int[Math.min(sampleSize, totalPixels)];

		if (pixels instanceof byte[]) {
			byte[] bytePixels = (byte[]) pixels;
			for (int i = 0; i < sampledValues.length; i++) {
				sampledValues[i] = bytePixels[(int) (Math.random() * totalPixels)] & 0xFF; // Convert to unsigned int
			}
		} else if (pixels instanceof short[]) {
			short[] shortPixels = (short[]) pixels;
			for (int i = 0; i < sampledValues.length; i++) {
				sampledValues[i] = shortPixels[(int) (Math.random() * totalPixels)] & 0xFFFF; // Convert to unsigned int
			}
		} else if (pixels instanceof int[]) {
			int[] intPixels = (int[]) pixels;
			for (int i = 0; i < sampledValues.length; i++) {
				sampledValues[i] = intPixels[(int) (Math.random() * totalPixels)];
			}
		} else {
			throw new IllegalArgumentException("Unsupported image type: " + pixels.getClass().getSimpleName());
		}

		return sampledValues;
	}
	// Find the quantile corresponding to the MNI
	private int findQuantile(int[] sortedValues, int MNI) {
		int index = Arrays.binarySearch(sortedValues, MNI);
		if (index < 0) index = -index - 1; // Get the insertion point
		return (int) ((index / (double) sortedValues.length) * 10000); // Convert to percentage
	}

	private void updateQuantile() {
		if (selectedImageFile == null) {
			quantileLabel.setText("N/A");
			return;
		}

		ImagePlus image = IJ.openImage(selectedImageFile.getAbsolutePath());
		if (image != null) {
			ImageProcessor ip = image.getProcessor();
			int[] pixelValues = samplePixelValues(ip, 10000);

			if (pixelValues.length > 0) {
				Arrays.sort(pixelValues);
				int MNIValue = (int) MNISpinner.getValue();
				int quantileValue = findQuantile(pixelValues, MNIValue);
				quantileLabel.setText("MNI Quantile: " + quantileValue);
			} else {
				quantileLabel.setText("No valid pixels found.");
			}
		}
	}

	public void generateNoiseImages(JProgressBar progressBar) {
		if (stackFolderField.getText().isEmpty()) {
			JOptionPane.showMessageDialog(null, "No directory selected!", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}

		File directory = new File(stackFolderField.getText());
		File[] imageFiles = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".tif") || name.toLowerCase().endsWith(".tiff"));

		if (imageFiles == null || imageFiles.length == 0) {
			JOptionPane.showMessageDialog(null, "No TIFF images found!", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}

		// ✅ Show progress bar when execution starts
		SwingUtilities.invokeLater(() -> progressBar.setVisible(true));

		// Create output directory if it doesn't exist
		File outputDir = new File(directory, "noise_images");
		if (!outputDir.exists()) outputDir.mkdir();

		int totalFiles = imageFiles.length;
		progressBar.setValue(0);

		// ✅ Precompute the SG kernel once per stack
		int filterSize = (int) filterSizeSpinner.getValue();
		float[] sgKernel = getCachedSavitzkyGolayKernel(filterSize); // Compute once

		for (int i = 0; i < totalFiles; i++) {
			processImage(imageFiles[i], outputDir, sgKernel); // ✅ Pass the precomputed kernel
			int progress = (int) (((i + 1) / (double) totalFiles) * 100);

			// ✅ Update progress on the UI thread
			final int finalProgress = progress;
			SwingUtilities.invokeLater(() -> progressBar.setValue(finalProgress));
		}

		// ✅ Hide progress bar after completion
		SwingUtilities.invokeLater(() -> {
			progressBar.setValue(100);
			JOptionPane.showMessageDialog(null, "Noise images generated successfully!", "Done", JOptionPane.INFORMATION_MESSAGE);
			progressBar.setVisible(false);
		});
	}

	private void processImage(File imageFile, File outputDir, float[] sgKernel) {
		ImagePlus image = IJ.openImage(imageFile.getAbsolutePath());
		if (image == null) {
			System.err.println("Failed to open image: " + imageFile.getName());
			return;
		}

		ImageProcessor ip = image.getProcessor();

		// Compute median intensity
		ImageStatistics stats = ImageStatistics.getStatistics(ip, ImageStatistics.MEDIAN, null);
		int medianIntensity = (int) stats.median;

		// Get quantile threshold based on MNI
		int[] pixelValues = samplePixelValues(ip, 10000);
		Arrays.sort(pixelValues);
		int MNIValue = (int) MNISpinner.getValue();
		int quantileThreshold = findQuantile(pixelValues, MNIValue);

		// Replace pixels above the quantile with the median intensity
		replaceHighIntensityPixels(ip, quantileThreshold, medianIntensity);

		// ✅ Apply the precomputed SG filter instead of recalculating it
		applySavitzkyGolayFilter(ip, sgKernel);

		// Save the processed Noise image
		String outputName = "noise_" + imageFile.getName();
		File outputFile = new File(outputDir, outputName);
		IJ.saveAsTiff(new ImagePlus(outputName, ip), outputFile.getAbsolutePath());
	}


	// Replace pixels above the quantile threshold with the median intensity
	private void replaceHighIntensityPixels(ImageProcessor ip, int threshold, int median) {
		int width = ip.getWidth();
		int height = ip.getHeight();

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int pixelValue = ip.getPixel(x, y);
				if (pixelValue > threshold) {
					ip.putPixel(x, y, median);
				}
			}
		}
	}

	private void applySavitzkyGolayFilter(ImageProcessor ip, float[] sgKernel) {
		FloatProcessor fp = ip.convertToFloatProcessor();
		fp.convolve(sgKernel, (int) Math.sqrt(sgKernel.length), (int) Math.sqrt(sgKernel.length));

		// ✅ Convert back to ShortProcessor if necessary
		if (ip instanceof ij.process.ShortProcessor) {
			short[] shortPixels = new short[ip.getWidth() * ip.getHeight()];
			float[] floatPixels = (float[]) fp.getPixels();

			for (int i = 0; i < shortPixels.length; i++) {
				shortPixels[i] = (short) Math.round(floatPixels[i]); // ✅ Convert float to short safely
			}

			ip.setPixels(shortPixels); // ✅ Use correct pixel format
		} else {
			ip.setPixels(fp.getPixels()); // ✅ Set normally if not a ShortProcessor
		}
	}

	// ✅ Cached LUT for Savitzky-Golay kernels to avoid redundant calculations

	private float[] getCachedSavitzkyGolayKernel(int size) {
		if (sgKernelCache.containsKey(size)) {
			return sgKernelCache.get(size);
		}

		float[] kernel = generateSavitzkyGolayKernel(size);
		sgKernelCache.put(size, kernel);
		return kernel;
	}

	// ✅ HashMap to store precomputed SG kernels
	private final HashMap<Integer, float[]> sgKernelCache = new HashMap<>();


	// Generate a 2D Savitzky-Golay kernel dynamically
	private float[] generateSavitzkyGolayKernel(int size) {
		float[] kernel = new float[size * size];
		int center = size / 2;
		double sigma = size / 3.0; // Standard deviation for Gaussian-like weighting

		// Create a circular mask
		double sum = 0;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				int dx = x - center;
				int dy = y - center;
				double distance = Math.sqrt(dx * dx + dy * dy);

				if (distance <= center) { // Inside the circular mask
					kernel[y * size + x] = (float) computeSGCoefficient(dx, dy, size, sigma);
					sum += kernel[y * size + x];
				} else {
					kernel[y * size + x] = 0; // Outside the circular area
				}
			}
		}

		// Normalize the kernel so sum = 1
		if (sum > 0) {
			for (int i = 0; i < kernel.length; i++) {
				kernel[i] /= sum;
			}
		}

		return kernel;
	}

	// Compute SG coefficient with a Gaussian-like weighting inside the circle
	private double computeSGCoefficient(int x, int y, int size, double sigma) {
		return Math.exp(-(x * x + y * y) / (2.0 * sigma * sigma)); // Gaussian decay
	}
	// Compute SG coefficient (simplified polynomial fit)
	private double computeSGCoefficient(int x, int y, int size) {
		return Math.exp(-(x * x + y * y) / (2.0 * (size / 2.0) * (size / 2.0))); // Gaussian-like approximation
	}

	public void generateNormalizedImages(JProgressBar progressBar) {
		if (stackFolderField.getText().isEmpty()) {
			JOptionPane.showMessageDialog(null, "No directory selected!", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}

		File directory = new File(stackFolderField.getText());
		File noiseDir = new File(stackFolderField.getText(), "noise_images");
		File normDir = new File(directory, "norm_noise");

		if (!noiseDir.exists()) {
			JOptionPane.showMessageDialog(null, "noise images not found!", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		if (!normDir.exists()) normDir.mkdir(); // Create the output folder if it doesn’t exist

		File[] imageFiles = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".tif") || name.toLowerCase().endsWith(".tiff"));

		if (imageFiles == null || imageFiles.length == 0) {
			JOptionPane.showMessageDialog(null, "No TIFF images found!", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}

		SwingUtilities.invokeLater(() -> progressBar.setVisible(true));

		int totalFiles = imageFiles.length;
		progressBar.setValue(0);

		for (int i = 0; i < totalFiles; i++) {
			File originalImageFile = imageFiles[i];
			File noiseImageFile = new File(noiseDir, "noise_" + originalImageFile.getName());

			if (!noiseImageFile.exists()) {
				System.err.println("noise image not found for: " + originalImageFile.getName());
				continue;
			}

			normalizeAndSave(originalImageFile, noiseImageFile, normDir);

			int progress = (int) (((i + 1) / (double) totalFiles) * 100);
			final int finalProgress = progress;
			SwingUtilities.invokeLater(() -> progressBar.setValue(finalProgress));
		}

		SwingUtilities.invokeLater(() -> {
			progressBar.setValue(100);
			JOptionPane.showMessageDialog(null, "Normalized images saved in 'norm_noise'!", "Done", JOptionPane.INFORMATION_MESSAGE);
			progressBar.setVisible(false);
		});
	}

	private void normalizeAndSave(File originalFile, File noiseFile, File normDir) {
		ImagePlus originalImage = IJ.openImage(originalFile.getAbsolutePath());
		ImagePlus noiseImage = IJ.openImage(noiseFile.getAbsolutePath());

		if (originalImage == null || noiseImage == null) {
			System.err.println("Error opening images: " + originalFile.getName());
			return;
		}

		ImageProcessor originalIp = originalImage.getProcessor().convertToFloatProcessor();
		ImageProcessor noiseIp = noiseImage.getProcessor().convertToFloatProcessor();

		// Normalize noise image to [0,1]
		double minnoise = noiseIp.getMin();
		double maxnoise = noiseIp.getMax();
		noiseIp.subtract(minnoise);
		noiseIp.multiply(1.0 / (maxnoise - minnoise)); // Scale to [0,1]

		// Divide original by normalized noise (avoid divide-by-zero)
		int width = originalIp.getWidth();
		int height = originalIp.getHeight();

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				float origValue = originalIp.getPixelValue(x, y);
				float backValue = noiseIp.getPixelValue(x, y);

				if (backValue > 0) { // Avoid divide-by-zero
					originalIp.putPixelValue(x, y, origValue / backValue);
				} else {
					originalIp.putPixelValue(x, y, origValue); // Keep original value if noise is zero
				}
			}
		}

		// Save normalized image
		String outputName = "norm_" + originalFile.getName();
		File outputFile = new File(normDir, outputName);
		IJ.saveAsTiff(new ImagePlus(outputName, originalIp), outputFile.getAbsolutePath());
	}

	public static void main(String[] args) {
		// Ensure GUI runs on the Event Dispatch Thread (EDT)
		javax.swing.SwingUtilities.invokeLater(() -> {
			new ImageJ();
			Intensify3D gui = new Intensify3D();
			gui.showDialog();
		});
	}
}