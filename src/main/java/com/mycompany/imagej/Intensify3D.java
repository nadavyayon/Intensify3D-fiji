package com.mycompany.imagej;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import com.mycompany.imagej.SavitzkyGolay2D; // Corrected import
import ij.process.ShortProcessor;

import java.util.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FilenameFilter;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

public class Intensify3D {

	// ... (GUI components - no changes here) ...
	private JTextField stackFolderField;       // Text field to display/enter the image stack directory.
	private JLabel imageCountLabel;          // Label to display the number of TIFF images found.
	private JLabel MNILabel;                // Label for MNI (Max Noise Intensity)
	private JSpinner MNISpinner;            // Spinner to select the MNI value.
	private File selectedImageFile;           // Stores a reference to a selected image file (used for preview/calculations).

	// Add this field to store filter size
	private JSpinner filterSizeSpinner;      // Spinner to select the Savitzky-Golay filter size.
	private JLabel quantileLabel; // GUI label for displaying quantile

	public void showDialog() {
		JFrame frame = new JFrame("Intensify3D - Image Normalization");
		frame.setSize(600, 450);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setLayout(new GridLayout(11, 2)); // Increased row count to fit new elements

		// --- Row 1: Directory Selection ---
		frame.add(new JLabel("Select Image Stack Directory:"));  // Label for the directory selection
		JPanel pathPanel = new JPanel(new BorderLayout());      // Panel to hold the text field and browse button
		stackFolderField = new JTextField();                    // Text field for the directory path
		JButton browseButton = new JButton("Browse...");      // Button to open a file chooser
		browseButton.addActionListener(this::browseForDirectory);    // Action listener for the browse button
		pathPanel.add(stackFolderField, BorderLayout.CENTER);     // Add text field to the center of the panel
		pathPanel.add(browseButton, BorderLayout.EAST);        // Add browse button to the right of the panel
		frame.add(pathPanel);                                   // Add the panel to the frame

		// --- Row 2: Image Count ---
		frame.add(new JLabel("Images to Process:"));            // Label for image count
		imageCountLabel = new JLabel("0");                      // Label to display the number of images
		frame.add(imageCountLabel);                             // Add image count label to the frame

		// ---  Image preview ---
		JButton previewButton = new JButton("Preview Image");
		previewButton.addActionListener(this::previewExampleImage);
		frame.add(previewButton);
		frame.add(new JLabel(""));

		// ---  MNI selection ---
		frame.add(new JLabel("Max Noise Intensity (MNI):"));
		MNISpinner = new JSpinner(new SpinnerNumberModel(50, 0, 65535, 1));
		MNISpinner.addChangeListener(e -> updateQuantile());
		frame.add(MNISpinner);

		frame.add(new JLabel("MNI Quantile (from 10000):"));
		quantileLabel = new JLabel("N/A");
		frame.add(quantileLabel);


		// --- Row 3: Filter Size Selection ---
		frame.add(new JLabel("Filter Size:"));                   // Label for the filter size spinner
		filterSizeSpinner = new JSpinner(new SpinnerNumberModel(3, 3, 100000000, 1)); // Spinner for filter size (default 3, min 3, step 1)
		frame.add(filterSizeSpinner);                            // Add filter size spinner to the frame

		// --- Row 4: Progress Bar ---
		frame.add(new JLabel("Progress:"));                      // Label for the progress bar
		JProgressBar progressBar = new JProgressBar(0, 100);     // Progress bar (0-100%)
		progressBar.setStringPainted(true);                    // Display the percentage value
		progressBar.setVisible(false);                         // Initially hidden
		frame.add(progressBar);                                 // Add progress bar to the frame

		// --- Row 5: Status Label ---
		JLabel statusLabel = new JLabel("Status: Waiting...");   // Status label to display messages
		frame.add(statusLabel);                                 // Add status label to the frame

		// --- Row 6 & 7: Execute Buttons ---
		JButton executeButton = new JButton("Generate Noise Images"); // Button to start noise image generation
		executeButton.addActionListener(e -> new Thread(() -> {      // Run in a separate thread to prevent GUI freezing
			statusLabel.setText("Status: Processing Noise Images..."); // Update status label
			generateNoiseImages(progressBar);                           // Call method to generate noise images
			statusLabel.setText("Status: Noise Images Generated.");      // Update status label
		}).start());
		frame.add(executeButton);                                       // Add execute button to the frame

		JButton normalizeButton = new JButton("Normalize Images");      // Button to start image normalization
		normalizeButton.addActionListener(e -> new Thread(() -> {        // Run in a separate thread
			statusLabel.setText("Status: Normalizing Images...");      // Update status label
			generateNormalizedImages(progressBar);                      // Call method to normalize images
			statusLabel.setText("Status: Normalization Complete.");    // Update status label
		}).start());
		frame.add(normalizeButton);                                     // Add normalize button to the frame

		frame.setVisible(true);                                         // Make the frame visible
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
		File[] tiffFiles = directory.listFiles((FilenameFilter) (dir, name) -> name.toLowerCase().endsWith(".tif") || name.toLowerCase().endsWith(".tiff"));
		int count = (tiffFiles == null) ? 0 : tiffFiles.length;
		imageCountLabel.setText(String.valueOf(count));

		if (count > 0) {
			selectedImageFile = tiffFiles[0];
		}
	}

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
				double autoMaxMNI = ip.getMaxThreshold();

				if (Double.isNaN(autoMaxMNI)) {
					autoMaxMNI = ip.getMax();
				}
				MNISpinner.setValue((int) autoMaxMNI);

				int quantileValue = findQuantile(pixelValues, (int) autoMaxMNI);
				quantileLabel.setText("MNI Quantile: " + quantileValue);
			} else {
				quantileLabel.setText("No valid pixels found.");
			}

			updateFilterSizeBounds();
		} else {
			JOptionPane.showMessageDialog(null, "Failed to open the image!", "Error", JOptionPane.ERROR_MESSAGE);
		}
	}
	private int[] samplePixelValues(ImageProcessor ip, int sampleSize) {
		Object pixels = ip.getPixels();
		int totalPixels = ip.getWidth() * ip.getHeight();
		int[] sampledValues = new int[Math.min(sampleSize, totalPixels)];

		if (pixels instanceof byte[]) {
			byte[] bytePixels = (byte[]) pixels;
			for (int i = 0; i < sampledValues.length; i++) {
				sampledValues[i] = bytePixels[(int) (Math.random() * totalPixels)] & 0xFF;
			}
		} else if (pixels instanceof short[]) {
			short[] shortPixels = (short[]) pixels;
			for (int i = 0; i < sampledValues.length; i++) {
				sampledValues[i] = shortPixels[(int) (Math.random() * totalPixels)] & 0xFFFF;
			}
		} else if (pixels instanceof int[]) {
			int[] intPixels = (int[]) pixels;
			for (int i = 0; i < sampledValues.length; i++) {
				sampledValues[i] = intPixels[(int) (Math.random() * totalPixels)];
			}
		} else if (pixels instanceof float[]) {
			float[] floatPixels = (float[]) pixels;
			for (int i = 0; i < sampledValues.length; i++) {
				sampledValues[i] = (int) floatPixels[(int) (Math.random() * totalPixels)];
			}
		} else {
			throw new IllegalArgumentException("Unsupported image type: " + pixels.getClass().getSimpleName());
		}
		return sampledValues;
	}

	private int findQuantile(int[] sortedValues, int MNI) {
		int index = Arrays.binarySearch(sortedValues, MNI);
		if (index < 0) index = -index - 1;
		return (int) ((index / (double) sortedValues.length) * 10000);
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
		File outputDir = new File(directory, "noise_images");
		if (!outputDir.exists()) outputDir.mkdir();

		File[] imageFiles = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".tif") || name.toLowerCase().endsWith(".tiff"));
		if (imageFiles == null || imageFiles.length == 0) {
			JOptionPane.showMessageDialog(null, "No TIFF images found!", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}

		Arrays.sort(imageFiles, Comparator.comparing(File::getName));

		SwingUtilities.invokeLater(() -> progressBar.setVisible(true));
		progressBar.setValue(0);

		int totalFiles = imageFiles.length;
		int filterSize = (int) filterSizeSpinner.getValue();

		for (int i = 0; i < totalFiles; i++) {
			processImage(imageFiles[i], outputDir, filterSize); // Pass filterSize here
			updateProgress(progressBar, i, totalFiles);
		}

		SwingUtilities.invokeLater(() -> {
			progressBar.setValue(100);
			JOptionPane.showMessageDialog(null, "Noise images generated successfully!", "Done", JOptionPane.INFORMATION_MESSAGE);
			progressBar.setVisible(false);
		});
	}

	private void processImage(File imageFile, File outputDir, int filterSize) {
		ImagePlus image = IJ.openImage(imageFile.getAbsolutePath());
		if (image == null) return;

		ImageProcessor ip = image.getProcessor();
		printImageStats("Original", imageFile.getName(), ip);

		int medianIntensity = computeMedian(ip);
		int threshold = computeThreshold(ip);

		replaceHighIntensityPixels(ip, threshold, medianIntensity);
		printImageStats("After Thresholding", imageFile.getName(), ip);

		// *** CORRECTED: Call applySavitzkyGolayFilter with the ImageProcessor ***
		applySavitzkyGolayFilter(ip, filterSize);
		printImageStats("After SG Filter", imageFile.getName(), ip);

		saveImage(ip, outputDir, imageFile.getName());
	}

	private int computeMedian(ImageProcessor ip) {
		ImageStatistics stats = ImageStatistics.getStatistics(ip, ImageStatistics.MEDIAN, null);
		return (int) stats.median;
	}


	private int computeThreshold(ImageProcessor ip) {
		int[] pixelValues = samplePixelValues(ip, 10000);
		Arrays.sort(pixelValues);
		int referenceQuantile = getReferenceQuantile(ip, (int) MNISpinner.getValue());
		int quantileIndex = Math.min((int) (referenceQuantile / 10000.0 * pixelValues.length), pixelValues.length - 1);
		return pixelValues[quantileIndex];
	}

	private int getReferenceQuantile(ImageProcessor ip, int MNIValue) {
		int[] pixelValues = samplePixelValues(ip, 10000);
		if (pixelValues.length > 0) {
			Arrays.sort(pixelValues);
			return findQuantile(pixelValues, MNIValue);
		} else {
			return 10000;
		}
	}

	private void saveImage(ImageProcessor ip, File outputDir, String originalName) {
		File outputFile = new File(outputDir, "noise_" + originalName);
		int width = ip.getWidth();
		int height = ip.getHeight();

		if (!(ip instanceof ShortProcessor)) {
			FloatProcessor fp = ip.convertToFloatProcessor();
			float[] floatPixels = (float[]) fp.getPixels();
			short[] shortPixels = new short[width * height];
			for (int i = 0; i < floatPixels.length; i++) {
				shortPixels[i] = (short) Math.min(Math.max(floatPixels[i], 0), 65535); // Clamp
			}
			ShortProcessor shortIp = new ShortProcessor(width, height);
			shortIp.setPixels(shortPixels);
			ip = shortIp;
		}

		IJ.saveAsTiff(new ImagePlus(outputFile.getName(), ip), outputFile.getAbsolutePath());
	}

	private void updateProgress(JProgressBar progressBar, int currentIndex, int totalFiles) {
		int progress = (int) (((currentIndex + 1) / (double) totalFiles) * 100);
		SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
	}

	private void printImageStats(String stage, String imageName, ImageProcessor ip) {
		ImageStatistics stats = ImageStatistics.getStatistics(ip, ImageStatistics.MEDIAN + ImageStatistics.MIN_MAX, null);
		System.out.println(stage + " | Image: " + imageName +
				" | Median: " + stats.median +
				" | Min: " + stats.min + " | Max: " + stats.max);
	}

	/**
	 * CORRECTED:  This method now performs the ENTIRE filtering process, including
	 * normalization, calling SavitzkyGolay2D.applyFilter, and de-normalization.  It
	 * operates on the ImageProcessor directly.
	 */
	private void applySavitzkyGolayFilter(ImageProcessor ip, int filterSize) {
		// 1. Convert to FloatProcessor
		FloatProcessor fp = ip.convertToFloatProcessor();

		// 2. Get original min/max
		double origMin = fp.getMin();
		double origMax = fp.getMax();

		// 3. Add a small offset *before* normalization (CRITICAL for avoiding corner artifacts)
		double offset = (origMax - origMin) * 0.001; // 0.1% of the original range
		if (offset == 0) offset = 1e-6; //handle flat images

		// 4. Normalize to (approximately) 0-1 range, WITH the offset
		if (origMax > origMin) { // Avoid division by zero if image is completely flat
			fp.add(offset); // Add to all pixels
			fp.subtract(origMin);
			fp.multiply(1.0 / (origMax - origMin + offset));
		}


		// 5. Call the SavitzkyGolay2D filter (which now does padding correctly)
		SavitzkyGolay2D.applyFilter(fp, filterSize);

		// 6. Restore original intensity range, accounting for the offset
		fp.multiply(origMax - origMin + offset);
		fp.add(origMin);
		fp.subtract(offset);

		// 7. Convert back to ShortProcessor (if needed), with clamping
		short[] shortPixels = new short[fp.getWidth() * fp.getHeight()];
		float[] floatPixels = (float[]) fp.getPixels();

		for (int i = 0; i < floatPixels.length; i++) {
			shortPixels[i] = (short) Math.min(Math.max(floatPixels[i], 0), 65535); // Clamp
		}

		ShortProcessor shortIp = new ShortProcessor(fp.getWidth(), fp.getHeight());
		shortIp.setPixels(shortPixels);
		ip.setPixels(shortIp.getPixels()); // Set the result back to the *original* ImageProcessor
	}

	private void replaceHighIntensityPixels(ImageProcessor ip, int threshold, int median) {
		int width = ip.getWidth();
		int height = ip.getHeight();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if (ip.getPixel(x, y) > threshold) {
					ip.putPixel(x, y, median);
				}
			}
		}
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
			JOptionPane.showMessageDialog(null, "Noise images not found!", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		if (!normDir.exists()) normDir.mkdir();

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
				System.err.println("Noise image not found for: " + originalImageFile.getName());
				continue;
			}

			normalizeAndSave(originalImageFile, noiseImageFile, normDir);
			updateProgress(progressBar, i, totalFiles);
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

		// Normalize noise image to max 1
		double maxNoise = noiseIp.getMax(); // Use a consistent naming convention (maxNoise, not maxnoise)
		noiseIp.multiply(1.0 / maxNoise);

		// Divide original by normalized noise (avoid divide-by-zero)
		int width = originalIp.getWidth();
		int height = originalIp.getHeight();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				float origValue = originalIp.getPixelValue(x, y);
				float backValue = noiseIp.getPixelValue(x, y);
				if (backValue > 0) {
					originalIp.putPixelValue(x, y, origValue / backValue);
				} else {
					originalIp.putPixelValue(x, y, origValue); // Keep the original value if noise is zero
				}
			}
		}

		// Save normalized image
		String outputName = "norm_" + originalFile.getName();
		File outputFile = new File(normDir, outputName);
		ImageProcessor ip = originalIp.convertToShortProcessor();  // Convert to 16-bit
		ImagePlus outputImage = new ImagePlus(outputName, ip);
		IJ.saveAsTiff(outputImage, outputFile.getAbsolutePath());
	}

	public static void main(String[] args) {
		javax.swing.SwingUtilities.invokeLater(() -> {
			new ImageJ();
			Intensify3D gui = new Intensify3D();
			gui.showDialog();
		});
	}
}