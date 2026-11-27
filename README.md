# Landmarker

A desktop app for digitizing 2D landmarks on specimen images for geometric morphometrics. You place landmarks and semi-landmark curves on your images, and Landmarker saves them as TPS files that work with tools like [geomorph](https://cran.r-project.org/package=geomorph) and MorphoJ.

- Place, drag, renumber and delete landmarks
- Draw smooth open curves or closed outlines and resample them into evenly spaced semi-landmarks
- Turn semi-landmarks into regular landmarks
- Set a scale from a known distance and measure lengths, perimeters and areas in real units
- Work on many specimens in one TPS file, with undo/redo

## Install

Download the latest installer from the [Releases page](https://github.com/tomayyeung/landmarker/releases):

| Platform | File |
|---|---|
| macOS (Apple Silicon) | `Landmarker-<version>-macos-arm64.dmg` |
| macOS (Intel) | `Landmarker-<version>-macos-x64.dmg` |
| Windows | `Landmarker-<version>-windows-x64.msi` |
| Any OS with Java 17+ | `Landmarker-<version>.jar` (run with `java -jar Landmarker-<version>.jar`) |

The installers come with their own Java runtime, so you don't need to install Java.

**macOS:** the app isn't signed. If macOS blocks it, open **System Settings → Privacy & Security** and click **Open Anyway**, or run:

```sh
xattr -dr com.apple.quarantine /Applications/Landmarker.app
```

**Windows:** if SmartScreen warns about an unrecognized app, click **More info → Run anyway**.

## Usage

### Basic workflow

1. **File → Open Images…** (`Cmd/Ctrl+O`) and pick one or more images. Each image becomes a specimen, with its ID taken from the file name.
2. Pick a mode from the toolbar and digitize. The status bar shows a hint for the current mode.
3. Optionally set a **Scale** on each specimen, or set it on one and use **Apply scale to all specimens**.
4. **File → Save TPS** (`Cmd/Ctrl+S`).

To pick up where you left off, use **File → Open TPS…** (`Cmd/Ctrl+Shift+O`). Landmarker looks for each image at the path in its `IMAGE=` line. Relative paths are resolved from the TPS file's folder, and if the image still isn't found, Landmarker looks for a file with the same name in that folder.

Images can be any format Java can read: PNG, JPEG, BMP, GIF, TIFF and WBMP.

### Modes

| Mode | What it does |
|---|---|
| **Select** | Click a landmark, semi-landmark or curve to select it, and drag points to move them. `Delete` removes the selected point; `Shift+Delete` removes the whole curve. |
| **Landmark** | Click empty space to add a landmark, or click an existing one to select and drag it. |
| **Scale** | Click two points a known distance apart, then type the real length and units. Clicks snap to existing points. |
| **Measure** | Click two points to measure the distance between them. Clicks snap to existing points. |
| **Curve** | Click control points along an open curve, then press `Enter`. You'll be asked how many semi-landmarks to generate. |
| **Outline** | Same as Curve, but the shape is closed. |
| **Trace LMs** | Click existing landmarks in order, then press `Enter` to fit a curve or outline through them. |

Curves are smooth splines through your control points, resampled into evenly spaced semi-landmarks. Open curves are drawn in blue and outlines in orange. You can drag individual semi-landmarks afterwards in Select mode.

The **Landmarks** panel lists every landmark with its coordinates. Select one and use ▲/▼ to renumber it.

The **Measurements** panel shows landmark count, path length, polygon perimeter and polygon area. When a curve is selected it also shows the curve's length, or the perimeter and area for an outline. Values are shown in real units once a scale is set, and in pixels before that.

### Converting curves to landmarks

Use **Edit → Convert Selected Curve to Landmarks** (`Cmd/Ctrl+L`), or the "All Curves" options to convert a whole specimen or every specimen at once. The semi-landmarks are added to the end of the landmark list in curve order, and the curve is removed. Any point that lands exactly on an existing landmark is skipped, such as the ends of a traced curve, so you don't get duplicates.

### Keyboard and mouse

| Action | Shortcut |
|---|---|
| Open images / Open TPS | `Cmd/Ctrl+O` / `Cmd/Ctrl+Shift+O` |
| Save / Save As | `Cmd/Ctrl+S` / `Cmd/Ctrl+Shift+S` |
| Undo / Redo | `Cmd/Ctrl+Z` / `Cmd/Ctrl+Shift+Z` |
| Convert selected curve to landmarks | `Cmd/Ctrl+L` |
| Previous / next specimen | `Cmd/Ctrl+[` / `Cmd/Ctrl+]` |
| Fit image to window | `F` or `Cmd/Ctrl+0` |
| Zoom in / out | Mouse wheel, `+` / `-` |
| Pan | Right-drag, middle-drag or `Alt/Option`-drag |
| Finish curve / outline / trace | `Enter` |
| Cancel in-progress curve or measurement | `Esc` |
| Delete selection (or last control point while drawing) | `Delete` / `Backspace` |

The single-key shortcuts only work when the image area has focus, so click the image first if a key doesn't respond.

## TPS format

Landmarker reads and writes standard TPS files. Each specimen looks like this:

```
LM=3
512.250 830.000
601.500 790.125
655.000 702.750
CURVES=1
POINTS=10
...
OUTLINES=1
POINTS=20
...
IMAGE=frog01.jpg
ID=frog01
SCALE=0.0125
COMMENT=left side
```

- **Coordinates** are image pixels, written with 3 decimal places. **File → Flip Y on save/load** is on by default and puts the origin at the bottom-left of the image, matching tpsDig. Flipping needs the image height, so if an image can't be found its specimen is saved unflipped and you'll get a warning.
- **`CURVES=` / `OUTLINES=`** store open and closed semi-landmark curves, each as a `POINTS=` block. If your analysis software doesn't read these tags, convert the curves to landmarks before saving.
- **`IMAGE=`** is written relative to the TPS file if the image is in the same folder or a subfolder, and as an absolute path otherwise.
- **`SCALE=`** is real units per pixel, the same convention tpsDig and geomorph use. The unit name (for example `mm`) is only used for display and isn't saved in the file.
- **On load:** `LM3=` files are accepted but their z coordinates are dropped, and unrecognized tags are ignored with a warning.

## Building from source

You need JDK 17 or newer and Maven.

```sh
mvn package                     # compiles, runs tests, builds target/landmarker.jar
java -jar target/landmarker.jar
```

Run only the tests with `mvn test`.

### Releasing

Push a version tag and the [Release workflow](.github/workflows/release.yml) will build the macOS and Windows installers and publish a GitHub Release:

```sh
git tag v1.2.0
git push origin v1.2.0
```

Tags with a suffix (like `v1.2.0-beta`) are published as pre-releases. You can also start the workflow by hand from the Actions tab to build the installers as downloadable artifacts without publishing a release.

## License

[MIT](LICENSE)
