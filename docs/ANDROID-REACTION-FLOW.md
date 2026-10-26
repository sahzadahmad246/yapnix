# Android reaction rounds

Home presents game families (database categories), then sub-games (database games). It shows a wordmark, optional login/profile link, native Compose floating hero, last-played card and artwork cards. Content is downloaded once into app-private files. Images are decoded before Play; recent decoded games are cached within 32 MiB, and active rounds have a heap-aware image decode budget. Standard five-picture rounds decode up to 720 pixels per edge. Game progress remains local with optional account sync.

The square picture is visible while the system speaks the prompt, with no dark mask, artificial loading overlay or extra countdown. The five-second monotonic answer timer starts as soon as both the picture and prompt are ready. At zero, the answer is displayed and spoken. There is no speech validation or correct/missed scoring.

The reaction button enables optional camera capture before Play. A mirror setting is available in a bottom sheet. The image takes less than half the screen width on the left; its timer and reveal card sit below it. A fixed portrait camera sits on the right, aligned with the image top and answer-card bottom. Without recording the smaller square image and timer are centred. A dedicated 720 × 1280 game canvas is composed on main, uploaded through a worker-owned EGL/OpenGL texture to MediaRecorder's input surface, and encoded as H.264 plus AAC microphone audio. No MediaProjection or native screen recording is used. The same square crop and side-by-side geometry apply to preview and export; controls and system UI are excluded. The callback consumes camera bitmaps synchronously on main; the encoder owns a separate scratch frame.

Stopping finalizes on the worker. A file becomes visible only after validation finds video and audio tracks with samples and positive durations. The service keeps its finalizing state until cleanup is complete to prevent restart races. Failures log their stage under `YapnixRecording`. Leaving setup during startup stops the pending service. The header has back, a centred sub-game title and play/pause. Pause freezes the timer, narration and the current MP4 through MediaRecorder.pause; play resumes the same file. A short toast replaces the old pause dialog. Back asks for leave confirmation; leaving or backgrounding finalizes the video. Cancelling leave resumes a previously running round. Saved clips are flattened; there is no post-recording layer or timeline editor.

The Videos screen offers thumbnails, playback and seeking, explicit export/share, and confirmed deletion. Recordings stay on the device until exported or shared. Original synthesized sound cues can be regenerated with `python3 android/scripts/generate-game-sounds.py`; they require no network or runtime model.

## Device validation pending

The user chose to test the APK rather than attach a phone to ADB. Verify camera rotation/mirroring, side-by-side alignment, pause/resume within one MP4, leave confirmation, microphone audibility, EGL encoder compatibility, A/V sync, playback/seek, export/share, update installation and startup/stop/background/replay behavior on physical devices. Automated compilation and unit tests cannot prove the hardware capture path.

## Portrait encoding and updates

Export remains 9:16. The current live stage uses a compact layout; the export centres the same composition with a small Yapnix watermark. Camera mirror controls open in a closable bottom sheet. New recordings try 1080×1920 at 30fps, then 720×1280 at 24fps if Full HD setup fails. Existing flattened videos remain unchanged. The Reels library displays a three-column portrait grid and exports original MP4 bytes.

Camera frames use CameraX ImageAnalysis rather than the view-sized PreviewView bitmap, allowing native-resolution camera source independent of the small UI. Frame ownership remains explicit between analyzer and main; the encoder still owns its separate scratch bitmap. Game images decode at up to 1080 pixels under the existing heap budget.

Update downloads are in-app with progress/cancel/retry. The downloaded APK must match installed package/signature, have a higher version code, and match release version, size and available checksum. Installation is explicitly initiated through Android's installer. Existing v0.4 and older apps need this APK installed once before they can use the new updater.

## Narration

Each round randomly chooses among the highest-quality installed offline voices for the question language, preferring its region, and retains that narrator for prompts and reveals. Speech rate is 0.94 with the voice’s natural pitch. Missing TTS still falls back to visual play. Android’s Voice API has no reliable gender field, so a male/female choice cannot be guaranteed; available voices and naturalness depend on the installed engine. No remote speech service or microphone-based answer validation is introduced.

## Offline catalogue icons

Game and sub-game cards prefer an optional uploaded image; otherwise they use `icon_key` to render bundled Phosphor duotone vectors. The same source SVGs and colours generate Studio’s picker. Keys are cached with the catalogue, so icons remain available offline and do not depend on signed URLs. Unknown future keys fall back to the bundled game-controller icon. The MIT licence is included in application assets.
