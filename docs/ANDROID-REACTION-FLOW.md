# Android reaction rounds

Rounds show a locally decoded picture, speak the prompt, wait five seconds, then display and speak the answer. Speech recognition and answer scoring are disconnected from this flow. Completion and resume progress remain local, with optional existing account sync.

Home opens categories, then games. A game download commits local metadata only after its pictures are saved. Reopening a downloaded game reads local files; every image is decoded before Play becomes available. The catalogue is retained for offline launch. Downloaded games currently remain cached until application data is cleared.

Recording uses a dedicated 720 × 1280 canvas encoded to H.264, plus AAC microphone audio. MediaProjection is no longer used. The composition contains the game image, prompt, timer, revealed answer and front-camera texture. Navigation, system bars and camera adjustment controls are excluded. Drag the camera overlay to move it; pinch or use the Camera slider to resize it. Game geometry is independent of the camera overlay. Videos stay local until explicitly exported or shared.

## Physical-device checks still required

- Allow camera and microphone, start a round, speak, and confirm exported video has audible reaction audio and moving camera frames.
- Check front-camera orientation and mirroring, and drag/pinch/slider bounds at both size limits.
- Confirm the encoder accepts hardware-canvas frames on supported device vendors, including Android 8 and Android 15.
- Confirm the export contains only the composition, with no system UI or editing controls.
- Deny either permission and verify play without recording still works without microphone permission.
- Complete a download, switch pages, reopen the game, then relaunch offline and confirm every picture is sharp before the timer starts.
- Background, return, resume, finish, replay, and export; check video finalization and progress persistence.

This is live composition editing; saved recordings are flattened videos, without post-recording layer or timeline editing.
