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

## Integration audit and remaining work

The native implementation is kept in this repository. The checked-out project contains no Frame Folk source, and the referenced GitHub repository could not be fetched during this audit, so a file-by-file comparison or a claim of pixel-faithful UI parity is not possible here.

| Area | PhotoCraft implementation |
| --- | --- |
| Native UI | Android Views in `app/src/main/res/layout/activity_main.xml`, wired by `MainActivity.kt`; no WebView |
| Edit state and history | Central `EditState` and `EditingState`, with undo/redo and persisted snapshots |
| Pixel processing | Light/color adjustments, HSL mixer, color grading, filters, effects and local-mask compositing in `edit/` |
| Preview | Background-thread rendering and stale-request cancellation in `image/PreviewRenderer.kt` |
| Export | Full-resolution tiled JPEG processing, crop and local-mask application in `export/JpegExporter.kt` |
| Masks and presets | Editable mask layers, subject/background segmentation, brush and eraser, linear/radial gradients, sampled color/luminance ranges, inversion, visibility, combination modes, local edits and serialized custom presets |
| Delivery workflow | `.github/workflows` builds and uploads a debug APK on `main` pushes and manual dispatch |

The crop ratio selector now includes the requested 1:1, 3:2, 2:3, 4:3, 5:4 and 16:9 presets, alongside the existing free, 4:5 and 9:16 options. New ratio values are appended to the enum so existing saved ordinal values remain valid.

This is still not a complete implementation of the full editor brief. RGB/tonal curves; rotate, flip and straighten; PNG export; configurable detail radius/detail/masking; sky, landscape and object recognition; depth masks; and the requested color-grading wheels are not implemented. Subject detection uses the existing selfie-segmentation model. Color and luminance ranges are sampled around the tapped pixel. The current three-way grading controls use hue, saturation amount and luminance sliders rather than color wheels. Preset and activity-state serialization do not preserve painted mask coverage and every mask setting. Frame Folk source comparison and device-side visual verification also remain outstanding.

## Verification

The debug APK built successfully with Gradle 8.11.1, JDK 17 and Android SDK 36. The repository test runner now includes the masking and effects sources required by its current models, plus pixel tests for color grading and effects; the M2–M5.2, color-grading, effects and effects-history suites pass. The APK is at `app/build/outputs/apk/debug/app-debug.apk`. No device-side interaction or export verification has been performed here.


