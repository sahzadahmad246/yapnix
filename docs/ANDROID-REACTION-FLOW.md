# Android reaction rounds

Home opens categories, then games. Content is downloaded once into app-private files. Images are decoded before Play; recent decoded games are cached within 32 MiB, and active rounds have a heap-aware image decode budget. Standard five-picture rounds decode up to 720 pixels per edge. Game progress remains local with optional account sync.

The square picture is visible while the system speaks the prompt, with no dark mask, artificial loading overlay or extra countdown. The five-second monotonic answer timer starts as soon as both the picture and prompt are ready. At zero, the answer is displayed and spoken. There is no speech validation or correct/missed scoring.

Camera framing can be adjusted before Play and during recording: drag, pinch, size slider, mirror, round/portrait shape and reset. A dedicated 720 × 1280 game canvas is composed on main, uploaded through a worker-owned EGL/OpenGL texture to MediaRecorder's input surface, and encoded as H.264 plus AAC microphone audio. No MediaProjection or native screen recording is used. The same square crop and normalized camera positioning apply to preview and export; controls and system UI are excluded. The callback consumes camera bitmaps synchronously on main; the encoder owns a separate scratch frame.

Stopping finalizes on the worker. A file becomes visible only after validation finds video and audio tracks with samples and positive durations. The service keeps its finalizing state until cleanup is complete to prevent restart races. Failures log their stage under `YapnixRecording`. Leaving setup during startup stops the pending service. Pausing/backgrounding finalizes the current video; resuming continues gameplay without a new recording. Saved clips are flattened; there is no post-recording layer or timeline editor.

The Videos screen offers thumbnails, playback and seeking, explicit export/share, and confirmed deletion. Recordings stay on the device until exported or shared. Original synthesized sound cues can be regenerated with `python3 android/scripts/generate-game-sounds.py`; they require no network or runtime model.

## Device validation pending

The user chose to test the APK rather than attach a phone to ADB. Verify camera rotation/mirroring, shape and size controls, microphone audibility, EGL encoder compatibility, A/V sync, playback/seek, export/share, update installation and startup/stop/background/replay behavior on physical devices. Automated compilation and unit tests cannot prove the hardware capture path.

## v0.5 portrait studio

Live preview and export share a 9:16 coordinate layout. The square question ends above a separate reaction pane; camera transformations are constrained to that pane. Camera controls open in a closable bottom sheet. New recordings try 1080×1920 at 30fps, then 720×1280 at 24fps if Full HD setup fails. Existing flattened videos remain unchanged. The Reels library displays a three-column portrait grid and exports original MP4 bytes.

Camera frames use CameraX ImageAnalysis rather than the view-sized PreviewView bitmap, allowing native-resolution camera source independent of the small UI. Frame ownership remains explicit between analyzer and main; the encoder still owns its separate scratch bitmap. Game images decode at up to 1080 pixels under the existing heap budget.

Update downloads are in-app with progress/cancel/retry. The downloaded APK must match installed package/signature, have a higher version code, and match release version, size and available checksum. Installation is explicitly initiated through Android's installer. Existing v0.4 and older apps need this APK installed once before they can use the new updater.
