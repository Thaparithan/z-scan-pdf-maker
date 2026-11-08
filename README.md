# Z Scan: PDF Maker (v2.0.0)

A modern, 100% offline-first Android document scanner and multi-page PDF creator built with Jetpack Compose, CameraX, and Room Database.

Developed for **ZYNOR CREATION**.

---

## Features

- **Document Scanner (CameraX):** High-resolution document capture with real-time stability detection, contrast scoring, auto-capture trigger, and flash unit toggle.
- **Image Enhancement Pipeline:**
  - Original (Natural color)
  - Auto-Enhance (Document sharpness & white lifting)
  - Grayscale
  - Black & White (High-contrast ink thresholding)
  - 90° quick rotation and interactive precision cropping canvas
- **PDF Creation Studio:**
  - Multi-page document assembly & page reordering
  - A4, US Letter, and Fit-Image page size presets
  - Auto, Portrait, or Landscape orientation
  - High, Balanced, and Compact compression controls
  - Dual PDF engine: Android `PdfDocument` with ISO 32000-1 binary PDF 1.4 stream fallback
- **Built-in PDF Viewer & Direct Printing:**
  - Pinch-to-zoom (1x - 3x) with fast page navigation
  - Native Wi-Fi & Bluetooth printing via Android `PrintManager`
  - Share via system apps, rename, and export to any folder via Storage Access Framework
- **100% Offline & Private:** Zero network requirements for scanning and PDF generation. All files remain strictly on device.

---

## How to Run in Android Studio

1. **Prerequisites:** [Android Studio](https://developer.android.com/studio) (Meerkat / Ladybug or newer recommended) with Android SDK Platform 35 or 36 installed.
2. Open Android Studio.
3. Choose **Open** and select this directory: `d:\ZYNOR CREATION\ZYNOR SCAN\z-scan_-pdf-maker`.
4. Allow Gradle Sync to finish automatically (a `local.properties` file with your SDK path is already configured).
5. Select an Android Emulator or connected physical device and click **Run** (green play button).
