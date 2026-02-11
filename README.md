<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="100" height="100" alt="App icon">
</p>

<h1 align="center">SpeakKeys</h1>

SpeakKeys is an open-source Android voice keyboard (IME) that uses cloud-based speech recognition. It supports two backends:

- **Sarvam AI** - optimized for Hinglish (Hindi + English) speech recognition
- **OpenAI Whisper** - multilingual speech recognition

Originally based on [Vosk](https://alphacephei.com/vosk/android), the app has moved to cloud-based providers for improved accuracy.

## Setup

API keys are configured at runtime in the app. Go to **Settings > API** tab to enter your OpenAI and/or Sarvam API keys. Only backends with a configured API key will be available.

## Build

```bash
./gradlew assembleDebug
```

## Permissions

- INTERNET - required for cloud speech recognition.
- RECORD_AUDIO - this is a voice keyboard after all.

## Demo

https://github.com/user-attachments/assets/c64fb21e-2095-4a8b-b4b2-952d09631604
