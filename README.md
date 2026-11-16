# PhotoCraft Android

PhotoCraft is a native Kotlin photo editor for Android. The app uses Android views and the system Photo Picker; it does not embed the Frame Folk browser editor. Frame Folk was audited as a visual and interaction reference. Its HTML/CSS/JavaScript implementation is a browser UI, and several controls in that source are visual-only or have mismatched event IDs. PhotoCraft's Kotlin image pipeline remains the processing source.

## Current editor features

- Import a photograph with Android's photo picker and render a responsive preview.
- Non-destructive light and color adjustments, including exposure, brightness, contrast, highlights, shadows, whites, blacks, temperature, tint, saturation and vibrance.
- Eight HSL color channels and four-wheel Color Grading for shadows, midtones, highlights and the full image, with direct wheel controls, per-range luminance, Blending and Balance. The same grading controls are available on selected masks.
- RGB and tonal point curves, filters, texture, configurable sharpening, details, noise reduction, vignette and selective lens blur.
- Free and preset-ratio crop with on-image controls, rotation/straightening and horizontal/vertical flip.
- Subject/background selections, brush painting, erasing, spot heal, inversion, persisted masks and local adjustments.
- Preset management, undo/redo, reset and press-and-hold before/after comparison.
- Tiled JPEG or PNG export with size and quality choices. Global effects and local mask adjustments are included in the full-resolution export.

## Build

Install JDK 17, Android SDK platform 36 and Gradle 8.11.1 or newer, then run:

```powershell
gradle :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions builds the debug APK on pushes to `main` and on manual workflow dispatch. Download the `PhotoCraft-debug-APK` artifact from the workflow run.

## Integration audit and remaining work

The native implementation is kept in this repository. The checked-out project contains no Frame Folk source, and the referenced GitHub repository could not be fetched during this audit, so a file-by-file comparison or a claim of pixel-faithful UI parity is not possible here.

| Area | PhotoCraft implementation |
| --- | --- |
| Native UI | Android Views in `app/src/main/res/layout/activity_main.xml`, wired by `MainActivity.kt`; no WebView |
| Edit state and history | Central `EditState` and `EditingState`, with undo/redo and persisted snapshots |
| Pixel processing | Light/color adjustments, HSL mixer, color grading, tone curves, filters, effects and local-mask compositing in `edit/` |
| Preview | Background-thread rendering and stale-request cancellation in `image/PreviewRenderer.kt` |
| Export | Full-resolution tiled JPEG/PNG processing, crop, geometry and local-mask application in `export/JpegExporter.kt` |
| Masks and presets | Editable, persisted mask layers; subject/background segmentation; brush, eraser and healing; linear/radial gradients; sampled color/luminance ranges; inversion, visibility, combination modes and local edits |
| Delivery workflow | `.github/workflows` builds and uploads a debug APK on `main` pushes and manual dispatch |

The crop ratio selector now includes the requested 1:1, 3:2, 2:3, 4:3, 5:4 and 16:9 presets, alongside the existing free, 4:5 and 9:16 options. New ratio values are appended to the enum so existing saved ordinal values remain valid.

Parity with Lightroom is still partial. Sky, landscape and object recognition, depth masks, lens/perspective correction, and RAW/HDR workflows are not implemented. Subject selection uses ML Kit selfie segmentation; sampled color and luminance masks are tap-centered ranges rather than Lightroom's richer selection tools. Healing is a local clone-based spot repair, not Lightroom's content-aware healing. Color Grading follows the documented controls, but Adobe does not publish its internal pixel-processing formula, so processing is an independent approximation. Frame Folk source comparison and device-side visual verification remain outstanding.

## Verification

The debug and Android instrumentation APKs build successfully with Gradle 8.11.1, JDK 17 and Android SDK 36, and Android lint completes. The repository JVM runner covers the M2–M5.2 state, history, crop, export-safety, color-grading, mask, blur and heal suites. The instrumentation APK builds, but UI tests still require an attached emulator/device. The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

