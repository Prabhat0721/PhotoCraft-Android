# PhotoCraft — rebuilt Android source

Native Kotlin Android photo-editor starter project.

## Included in this source
- Dark editor interface and Android photo picker
- Basic adjustments: exposure, brightness, contrast, highlights, shadows, whites, blacks, temperature, tint, saturation and vibrance
- **8-colour HSL mixer:** Red, Orange, Yellow, Green, Aqua/Cyan, Blue, Purple and Magenta; each has Hue, Saturation and Luminance controls
- **Three-way colour grading:** draggable hue wheel and intensity slider independently for Shadows, Midtones and Highlights
- Looks/presets, undo/redo, reset, square crop, 90-degree rotation
- JPEG export to `Pictures/PhotoCraft`
- GitHub Actions workflow to build a debug APK

## Build APK
Push the project to a GitHub repository, then open **Actions → Build PhotoCraft APK → Run workflow**. Download the `PhotoCraft-debug-APK` artifact from a successful run.

## Status
Source changes for HSL and colour grading have been added. This environment does not have Gradle installed, so a successful Android compilation and device test have **not** yet been verified. This is a fresh baseline, not a verified replacement for every historical M0–M5.5 feature.
