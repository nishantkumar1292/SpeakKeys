<img align="left" width="80" height="80"
src="app/src/main/ic_launcher-playstore.png" alt="App icon">

# Sayboard

Sayboard is an open-source Android voice keyboard (IME) that uses cloud-based speech recognition. It supports two backends:

- **Sarvam AI** - optimized for Hinglish (Hindi + English) speech recognition
- **OpenAI Whisper** - multilingual speech recognition

Originally based on [Vosk](https://alphacephei.com/vosk/android), the app has moved to cloud-based providers for improved accuracy.

## Setup

API keys are required. Add them to `local.properties` in the project root:

```properties
SARVAM_API_SUBSCRIPTION_KEY=your_sarvam_key
OPENAI_API_KEY=your_openai_key
```

Only backends with a configured API key will be available in the app.

## Build

```bash
./gradlew assembleDebug
```

## Permissions

- INTERNET - required for cloud speech recognition.
- RECORD_AUDIO - this is a voice keyboard after all.
- POST_NOTIFICATIONS - to show download and import progress.
- FOREGROUND_SERVICE - for the RecognitionService.
- FOREGROUND_SERVICE_MICROPHONE - for the RecognitionService.
- QUERY_ALL_PACKAGES - due to a bug in Android, this permission is required for a speech RecognitionService to work properly (see https://github.com/Kaljurand/K6nele-service/issues/9).

## Screenshot

Screenshot of Sayboard:

https://github.com/ElishaAz/Sayboard/assets/26592879/58f1421e-0e10-488f-a7fa-4aa702f1cee2
