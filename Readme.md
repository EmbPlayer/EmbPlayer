# EmbPlayer 📱➡️🌐

## What is EmbPlayer?

**EmbPlayer is an Android application that turns your Android smartphone or tablet into a dedicated media playback host.**

Instead of controlling the app directly on the device, EmbPlayer lets **any device with a modern web browser** act as the remote control. Once installed on your host Android, simply scan a QR code from any browser-equipped device—and take full control instantly. **No app installation is required on the controller.**

**The two roles are simple:**
- **The Host (Media Player):** Your Android device running EmbPlayer. This is where the video/audio actually plays.
- **The Remote (Controller):** Any device with a modern web browser (Chrome, Edge, Firefox, Safari, etc.). **No installation needed**—just scan and control.

---

## ✨ Features

- **Host-Only Installation** – Install EmbPlayer only on the Android device that will play the media.  
- **Universal Browser Control** – Any device with a modern web browser becomes the remote—**without installing anything**.
- **Instant QR Connection** – No accounts, no pairing, no Bluetooth. Scan the QR code and the control panel loads immediately.
- **Local-Only & Private** – All communication stays on your private local network. No cloud servers, no external routing.
- **Supports a Wide Range of Media**:
  - **YouTube Links** – Videos, live streams, and full playlists.
  - **Pure Media URLs** – Direct audio streams, video streams, and live HLS/RTMP links.
  - **Country-Based Live Streams** – Curated lists of TV and radio channels (currently **Turkey (TR)** and **Bulgaria (BG)** – updated dynamically; tap refresh to get the latest).
- **Clean & Intuitive UI** – Minimalist design focused on hassle-free playback and control.
- **Lightweight & Fast** – Optimized for Android devices with minimal resource usage.

---

## 🛠️ How It Works

1. **Host Device (The Media Player)** – Install and launch EmbPlayer on the Android smartphone or tablet that will play the content.  
2. **Control Device (The Remote)** – Grab any device that runs a modern web browser (phone, tablet, laptop, desktop, etc.). Scan the QR code shown on the host—**no app installation required**, just a browser.
3. The QR code opens the web-based control panel in your browser.
4. From this browser panel:
   - Paste a **YouTube link** (video, live, or playlist).
   - Paste a **pure media URL** (audio, video, or live stream).
   - Pick a **preloaded country stream** from the TR or BG lists.
5. Playback begins on the host Android device. Use the browser interface to play/pause, adjust volume, seek, and switch streams seamlessly.

> **⚠️ Note:** Both the host and the controlling device must be connected to the **same local network** (Wi-Fi/Ethernet).

---

## 📦 Available Versions (Build Variants)

EmbPlayer is built in multiple flavours to support a wide range of Android devices. Each variant targets a different minimum Android version and includes a specific set of playback engines.

**Choose the APK that matches your host device's Android version** – the app will automatically use the most suitable engine for your media.

| Variant  | Android Min Version | Engines Available             |
|:---------| :--- |:------------------------------|
| **M21V** | 5.0+ (API 21) | ijkplayer, OEM, ExoPlayer,VLC |
| **M21**  | 5.0+ (API 21) | ijkplayer, OEM, ExoPlayer     |
| **L16V** | 4.4+ (API 17) | ijkplayer, OEM, ExoPlayer,VLC |
| **L16**  | 4.1+ (API 16) | ijkplayer, OEM, ExoPlayer     |
| **L14**  | 4.0+ (API 14) | ijkplayer, OEM                |

**Why so many variants?**
- **Android compatibility** – Older Android versions (e.g., 4.0–4.4) cannot run apps built for modern SDKs. These variants ensure EmbPlayer works on everything from legacy tablets to the latest smartphones.
- **Engine availability** – VLC and ExoPlayer require newer Android features and are not available (or stable) on very old systems, hence the `L14` variant sticks to ijkplayer + OEM.
- **Performance** – Even when multiple engines are present, the app uses build-specific default priorities to give you the best out‑of‑box experience.

---