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

### 1️⃣ Install in Fiji  
1. **Download the latest `Intensify3D-Fiji.jar`** from [Releases](https://github.com/nadavyayon/Intensify3D-Fiji/releases).  
2. **Copy the `.jar` file** to the **Fiji Plugins** folder:  
   - On **Windows**: `C:\Fiji.app\plugins\`  
   - On **Mac/Linux**: `~/Fiji.app/plugins/`  
3. **Restart Fiji**.  
4. The plugin will now appear under **Plugins > Intensify3D**.  

## 🚀 Usage  
1. **Launch Fiji**, then open *Intensify3D-Fiji* from the **Plugins** menu.  
2. **Select an image folder** containing **TIFF images**.  
3. **Set the MBI threshold** and preview the quantile distribution.  
4. **Adjust the filter size** for background smoothing.  
5. Click **Run** to process all images in the folder.  

## 🔧 Requirements  
- **Java 8+**  
- **Fiji (ImageJ-based distribution)**  

## 📜 License  
This project is released under the **MIT License**. See [LICENSE](https://github.com/nadavyayon/Intensify3D/blob/master/LICENSE) for details.  

## 👨‍💻 Author  
Developed by **Nadav Yayon** as part of an effort to enhance fluorescence microscopy intensity normalization using Fiji.  

---

🚀 *Happy imaging! Let me know if you have any issues or feature requests!*  