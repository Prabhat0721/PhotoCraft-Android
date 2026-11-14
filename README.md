# PhotoCraft Android

PhotoCraft is a native Kotlin photo editor for Android. The app uses Android views and the system Photo Picker; it does not embed the Frame Folk browser editor. Frame Folk was audited as a visual and interaction reference. Its HTML/CSS/JavaScript implementation is a browser UI, and several controls in that source are visual-only or have mismatched event IDs. PhotoCraft's Kotlin image pipeline remains the processing source.

## Current editor features

- Import a photograph with Android's photo picker and render a responsive preview.
- Non-destructive light and color adjustments, including exposure, brightness, contrast, highlights, shadows, whites, blacks, temperature, tint, saturation and vibrance.
- Eight HSL color channels and three-way shadow, midtone and highlight grading.
- Filters, texture, sharpness, details, noise reduction and vignette.
- Free and preset-ratio crop with an on-image crop overlay.
- Subject/background selections, brush painting, erasing, inversion and local adjustments.
- Preset management, undo/redo, reset and press-and-hold before/after comparison.
- Tiled JPEG export with size and quality choices. Global effects and local mask adjustments are included in the full-resolution export.

## Build

Install JDK 17, Android SDK platform 36 and Gradle 8.11.1 or newer, then run:

```powershell
gradle :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions builds the debug APK on pushes to `main` and on manual workflow dispatch. Download the `PhotoCraft-debug-APK` artifact from the workflow run.

## Audit notes and remaining work

The editor already contains native equivalents for the core Frame Folk workspace, tabs, adjustment panels, comparison, crop, and export. Existing image-processing code is reused in `app/src/main/kotlin/com/prabhat/photocraft/edit`, with preview rendering in `image/PreviewRenderer.kt` and full-resolution output in `export/JpegExporter.kt`.

This project is not yet a complete implementation of the full editor brief. RGB/tonal curves, rotation/flip/straighten, PNG export, the full requested crop-ratio set and complete detail controls are not implemented. Brush feather, size and flow are available in the masking panel. Presets and saved activity state preserve the current global effects, but do not yet preserve painted mask coverage or all mask settings. These gaps should be closed before describing the app as production-complete.

## Verification

The debug APK was built locally with JDK 17 and Android SDK 36. No device-side interaction or export verification has been performed in this environment.

