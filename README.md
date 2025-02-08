# Intensify3D-Fiji  
*A Fiji Plugin for Image Normalization and Background Correction*  

[![ImageJ](https://img.shields.io/badge/Fiji-ImageJ-green)](https://imagej.net/software/fiji/)  
[![Java](https://img.shields.io/badge/Java-8%2B-blue)](https://www.oracle.com/java/)  
[![License](https://img.shields.io/github/license/nadavyayon/Intensify3D)](https://github.com/nadavyayon/Intensify3D/blob/master/LICENSE)

## 📌 About  
**Intensify3D-Fiji** is a Fiji-based plugin for **intensity normalization** in fluorescence microscopy images. It is a reimplementation of the original **[Intensify3D MATLAB tool](https://github.com/nadavyayon/Intensify3D)**, designed for seamless integration with **ImageJ/Fiji**, providing an interactive GUI for efficient **background correction** and **image intensity normalization**.

## ✨ Features  
✅ **Image Preview** – Quickly visualize raw images.  
✅ **Max Background Intensity (MBI) Selection** – Dynamically choose an intensity threshold for background correction.  
✅ **Quantile Calculation** – Computes the quantile corresponding to the MBI from a sample of 10,000 pixels.  
✅ **Pixel Intensity Capping** – Pixels above the MBI threshold are replaced with the **median intensity**.  
✅ **Filter Processing**:  
   - **Custom Filter Size** – User-defined filter size for background smoothing.  
   - **Savitzky-Golay Filtering** – Applied **twice** to generate the background image.  
✅ **Intensity Normalization**:  
   - Background is normalized to **[0,1]**.  
   - Original image is converted to **32-bit** and divided by the background image.  

## 📥 Installation  
### 1️⃣ Download Fiji  
If you don’t have **Fiji**, download it from [Fiji website](https://imagej.net/software/fiji/).  

### 2️⃣ Clone This Repository  
```sh
git clone https://github.com/nadavyayon/Intensify3D-Fiji.git