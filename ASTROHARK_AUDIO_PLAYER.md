# Astrohark Audio Player Architecture

## Overview
`ChatAudioPlayer` is a dedicated utility class/component responsible for playing voice notes within the chat interface.

## Responsibilities
1. **MediaPlayer Lifecycle Management:** `prepare()`, `start()`, `pause()`, `stop()`, `release()`.
2. **Audio Focus:** Requesting and abandoning audio focus from the Android system so music apps pause during voice notes.
3. **State Management:** Tracking which audio file is currently playing (`isPlaying`, `currentUrl`, `progress`).
4. **UI Callbacks:** Notifying the UI of playback progress (current duration, max duration) to update progress bars and play/pause icons.

## Guidelines for Audio Feature Changes
* **Memory Leaks:** Always call `release()` on the MediaPlayer instance when the Activity/Fragment is destroyed.
* **Concurrency:** Ensure only one audio plays at a time. If a user clicks 'Play' on a new message, the previous playing audio must `stop()`.
* **Error Handling:** Implement `MediaPlayer.OnErrorListener` to gracefully handle corrupt audio files or network issues (if streaming).
* **State sync:** When playback completes (`onCompletion`), the UI state must accurately reflect the reset state.
