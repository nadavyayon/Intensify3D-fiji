package com.mycompany.imagej;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FilenameFilter;

/**
 * Intensify3D - Image Normalization Tool
 * Now includes a Browse button and counts TIFF images in the selected folder.
 *
 * @author Nadav Yayon
 */
public class Intensify3D implements PlugInFilter {
	protected ImagePlus image;

	// Image properties
	private int width;
	private int height;

	// Plugin parameters
	private double filterSize;
	private double stdNumber;
	private double maxTissueIntensity;
	private boolean hasBackground;
	private String normalizationType;
	private int threads;
	private String stackFolder;

	// GUI Components
	private JTextField stackFolderField;
	private JLabel imageCountLabel;

	@Override
	public int setup(String arg, ImagePlus imp) {
		if (arg.equals("about")) {
			showAbout();
			return DONE;
		}
		image = imp;
		return DOES_8G | DOES_16 | DOES_32 | DOES_RGB;
	}

	@Override
	public void run(ImageProcessor ip) {
		width = ip.getWidth();
		height = ip.getHeight();

		if (showDialog()) {
			process(ip);
			image.updateAndDraw();
		}
	}

	private boolean showDialog() {
		// Create a Swing-based GUI
		JFrame frame = new JFrame("Intensify3D - Image Normalization");
		frame.setSize(500, 250);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setLayout(new GridLayout(6, 2));

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

		// Normalization Parameters
		frame.add(new JLabel("Filter Size:"));
		JSpinner filterSizeSpinner = new JSpinner(new SpinnerNumberModel(3.0, 0.1, 10.0, 0.1));
		frame.add(filterSizeSpinner);

		frame.add(new JLabel("STD Number:"));
		JSpinner stdNumberSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 10.0, 0.1));
		frame.add(stdNumberSpinner);

		frame.add(new JLabel("Max Tissue Intensity:"));
		JSpinner maxIntensitySpinner = new JSpinner(new SpinnerNumberModel(255.0, 1.0, 65535.0, 1.0));
		frame.add(maxIntensitySpinner);

		// Show GUI
		frame.setVisible(true);
		return true;
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
	}

	public void process(ImageProcessor ip) {
		// Placeholder function
	}

	public void showAbout() {
		IJ.showMessage("Intensify3D", "A tool for fluorescent image normalization.");
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