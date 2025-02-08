package com.mycompany.imagej;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import java.util.Arrays;

import ij.gui.NewImage;
import ij.process.ImageProcessor;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;

/**
 * Intensify3D - Image Normalization Tool
 * Now includes:
 *  ✅ Image preview
 *  ✅ Max Background Intensity (MBI) selection
 *
 * @author Nadav Yayon
 */
public class Intensify3D {
	// GUI Components
	private JTextField stackFolderField;
	private JLabel imageCountLabel;
	private JLabel mbiLabel;
	private JSpinner mbiSpinner;
	private File selectedImageFile;

	// Add this field to store filter size
	private JSpinner filterSizeSpinner;

	public void showDialog() {
		// Create a Swing-based GUI
		JFrame frame = new JFrame("Intensify3D - Image Normalization");
		frame.setSize(600, 350); // Increased height to accommodate filter size
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setLayout(new GridLayout(8, 2)); // Increased rows

		// Stack Folder Selection
		frame.add(new JLabel("Select Image Stack Directory:"));
		JPanel pathPanel = new JPanel(new BorderLayout());
		stackFolderField = new JTextField();
		JButton browseButton = new JButton("Browse...");
		browseButton.addActionListener(this::browseForDirectory);
		pathPanel.add(stackFolderField, BorderLayout.CENTER);
		pathPanel.add(browseButton, BorderLayout.EAST);
		frame.add(pathPanel);

		// Display the number of TIFF images found
		frame.add(new JLabel("Images to Process:"));
		imageCountLabel = new JLabel("0");
		frame.add(imageCountLabel);

		// Button to Preview an Example Image
		JButton previewButton = new JButton("Preview Image");
		previewButton.addActionListener(this::previewExampleImage);
		frame.add(previewButton);
		frame.add(new JLabel("")); // Empty cell for alignment

		// Select Max Background Intensity (MBI)
		frame.add(new JLabel("Max Background Intensity (MBI):"));
		mbiSpinner = new JSpinner(new SpinnerNumberModel(50, 0, 65535, 1));
		mbiSpinner.addChangeListener(e -> updateQuantile());
		frame.add(mbiSpinner);

		// Quantile display
		frame.add(new JLabel("MBI Quantile (from 10000):"));
		quantileLabel = new JLabel("N/A");
		frame.add(quantileLabel);

		// **NEW: Filter Size Selection**
		frame.add(new JLabel("Filter Size:"));
		filterSizeSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1)); // Default = 3, min=1, max=20
		frame.add(filterSizeSpinner);

		// Show GUI
		frame.setVisible(true);
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
			IJ.run("Threshold...");

			ImageProcessor ip = image.getProcessor();
			int[] pixelValues = samplePixelValues(ip, 10000);

			if (pixelValues.length > 0) {
				Arrays.sort(pixelValues);
				int mbiValue = (int) mbiSpinner.getValue();
				int quantileValue = findQuantile(pixelValues, mbiValue);
				quantileLabel.setText("MBI Quantile: " + quantileValue);
			} else {
				quantileLabel.setText("No valid pixels found.");
			}
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
	// Find the quantile corresponding to the MBI
	private int findQuantile(int[] sortedValues, int mbi) {
		int index = Arrays.binarySearch(sortedValues, mbi);
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
				int mbiValue = (int) mbiSpinner.getValue();
				int quantileValue = findQuantile(pixelValues, mbiValue);
				quantileLabel.setText("MBI Quantile: " + quantileValue);
			} else {
				quantileLabel.setText("No valid pixels found.");
			}
		}
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