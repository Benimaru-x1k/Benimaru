<div align="center">

# 🚀️ Benimaru Tool ⚡️

**The Ultimate Rootless Android Optimization & Gaming Utility**

[![Platform: Android](https://img.shields.io/badge/Platform-Android%2011%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![API: Shizuku](https://img.shields.io/badge/API-Shizuku-blue?logo=android)](https://shizuku.rikka.app/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)

[![Release](https://img.shields.io/github/v/release/Benimaru-x1k/Benimaru?logo=github&label=Release)](../../releases/latest)
[![Downloads](https://img.shields.io/github/downloads/Benimaru-x1k/Benimaru/total?logo=github&label=Downloads&color=brightgreen)](../../releases)
[![Languages](https://img.shields.io/badge/Languages-Auto%20(Device%20Language)-0A84FF?logo=googletranslate&logoColor=white)](#-automatic-language)

</div>

**Benimaru Tool** is a powerful, rootless Android utility application designed to push your device to its limits. By utilizing the [Shizuku](https://shizuku.rikka.app/) API, this app executes advanced ADB shell commands directly from your device—delivering kernel-level performance tweaks, touch latency reduction, and display customization **without requiring root access or a PC** after the initial setup.

---

## ✨ Features

Benimaru Tool is packed with everything you need to optimize your device, categorized for easy navigation.


### 🎮 Gaming & Performance

| Feature | Description |
|:--------|:------------|
| 🚀 **Fixed Performance Mode** | Locks device performance to a fixed state to maintain consistent frame rates during heavy workloads. Standard and Aggressive modes. |
| 🔥 **Disable Thermal Throttling** | Bypasses software temperature limits to prevent sudden frame drops and CPU throttling during long gaming sessions. |
| 🕹️ **Native Game Mode API** (Android 12+) | Unlocks deep performance by dropping system power limits and aggressively prioritizing CPU/GPU resources for your active game. |
| ⚡ **Touch Latency Reduction** | Increases the touch sampling rate and minimizes long-press timeouts for lightning-fast, precise screen interactions. |
| 🧭 **Fix Gyro Delay** | Resets the sensors, turns off Battery Saver and measures your gyroscope's real update rate, so you can tell whether aim lag comes from your phone or the game. |
| 🎮 **Game Launcher & Optimizer** | Automatically detects installed games to launch instantly or **pre-compile their DEX code (Speed Profile)** to eliminate in-game stutters. |
| 🎯 **FPS Crosshair Overlay** | Enable a customizable, center-screen crosshair to improve accuracy in mobile shooters. |
| 👆 **Auto Click & Auto Swipe** | A draggable floating ▶/■ button that repeats taps or swipes at your chosen interval. Choose Auto click, Auto swipe, or Tap + swipe, and set the interval, tap position and swipe length. No Accessibility permission needed. |
| ✨ **Shader Optimization** ⭐ | Pick a game and **pre-compile it** to cut stutter and loading hitches, then choose its **GPU driver** (native, ANGLE or system default) where your device supports it. Needs Android 10+ for driver selection. |

### 📺 Display & Visuals

| Feature | Description |
|:--------|:------------|
| 🔄 **Custom Refresh Rate** | Force specific Minimum and Maximum screen refresh rates (e.g., lock your display to a buttery smooth 120Hz). |
| 📱 **Safe Resolution Changer** | Adjust your display resolution (and your own DPI) to boost gaming FPS or extend battery life. Includes an **automatic aspect ratio calculator** and a **6-second safe-revert countdown** to prevent permanent black screens. Available as a direct change or a floating menu. |
| 💨 **UI Animation Speedup** | Cuts system animation scales in half (0.5x) making app launches and system navigation feel twice as fast. |
| 🌫️ **Disable Window Blurs** *(Android 12+)* | Frees up valuable GPU resources by turning off expensive real-time background blurs. |
| 🌗 **Dynamic Theme Engine** | Modern Light/Dark mode toggle with gradient dashboard and gold/green outlined Material cards that adapt to your system aesthetic. |

### 🛠️ System Tweaks & Utilities

| Feature | Description |
|:--------|:------------|
| 💻 **Shell Execute** ⭐ | Pick your own `.sh` script and run it **live inside the app** with Shizuku shell access. Includes a script preview, a *reset-all-functions-first* warning to avoid conflicts, live streaming output, a Stop button and Copy output. |
| 📊 **Floating Hardware Monitor** | An MSI Afterburner-style draggable HUD showing live CPU clock, RAM, storage, battery, refresh rate and network speed. Position it anywhere without obstructing gameplay. |
| 🧠 **RAM Booster** | See which apps use the most memory right now and stop the heavy ones in one tap. |
| 🧊 **Freeze Bloatware** | Freeze apps you don't use so they stop using RAM and background CPU. Fully reversible with one tap. |
| 🧹 **Clear App Caches** | Removes temporary cache files from every app to free up storage. Your accounts and saved data stay safe. |
| 💽 **Storage Optimizer (Fstrim)** | Forces the storage controller to wipe deleted data blocks, instantly restoring optimal read/write speeds. |
| 📶 **Network Tweaks** | Prioritizes network traffic and reduces ping for a lag-free mobile data experience. |
| 🌐 **Custom DNS Selector** | Set a system-wide private DNS (Cloudflare, Quad9, Control D, AdGuard, CleanBrowsing Family, etc.) via ADB for ad blocking and tracker protection—no VPN required. |
| 🧹 **System Optimization** | Clears background processes and aggressively reallocates resources to your active applications. |
| 🔕 **Gaming Focus Mode** | Blocks intrusive heads-up notification banners from ruining your matches. |
| 🏦 **Hide Developer Options** | Instantly toggles off Developer Options so banking apps and anti-cheat games run without triggering security warnings. |
| 📸 **Flag Secure** | Stops apps from blocking screenshots and screen recording, so you can capture screens that normally come out black. Works on debuggable ROMs (userdebug and many custom ROMs). |
| 🖼️ **Texture Compression and Resizing** ⭐ | Shrinks a game's loose texture images (PNG, JPG, WebP) by recompressing or resizing them to 75% or 50%, saving storage and memory. Originals are **backed up first** and can be restored anytime. Packed formats (bundles, OBB, PAK, ASTC/ETC) are left untouched. |


### ⚙️ App Experience

| Feature | Description |
|:--------|:------------|
| 🌍 **Automatic Language** | The app follows your device language (Bengali, Portuguese, Hindi, Arabic and many more). See [Automatic Language](#-automatic-language). |
| 🛡️ **Anti-Tamper Security** | Built-in SHA-256 signature verification prevents the execution of modified, repackaged, or unofficial APKs. |
| 📲 **In-App Auto Updater** | Automatically pings the official GitHub repository for new releases and handles seamless updates internally. |
| ⏪ **One-Tap Reset** | Panic button to instantly revert all tweaks back to your device's stock default settings. |

---

## 🌍 Automatic Language

Benimaru Tool ships **one English source** and translates itself to your device language using on-device [Google ML Kit](https://developers.google.com/ml-kit/language/translation) translation.

- On the first launch in a non-English language, a short setup sheet downloads the language pack **once**.
- After that, the app works **offline** in your language.
- If the download fails (offline or unsupported language), the app stays in English and tries again later.
- Translations are machine-generated, so wording may differ slightly from a human translation.

---

## 📸 Screenshots

<table align="center">
  <tr>
    <th align="center">Home Screen</th>
    <th align="center">Tweaks Menu</th>
    <th align="center">Launch Optimize</th>
  </tr>
  <tr>
    <td align="center"><img src="images/Screenshot_20261007_135942_Benimaru.png" width="250" alt="Benimaru Tool Home Screen" /></td>
    <td align="center"><img src="images/Screenshot_20261007_140005_Benimaru.png" width="250" alt="Benimaru Tool Tweaks Menu" /></td>
    <td align="center"><img src="images/Screenshot_20261007_140013_Benimaru.png" width="250" alt="Benimaru Tool Hardware Monitor" /></td>
  </tr>
</table>

---

## 📋 Prerequisites

> [!IMPORTANT]
> Because Benimaru Tool modifies secure system settings, it **requires Shizuku** to function.

| Step | Requirement | Details |
|:----:|:------------|:--------|
| 1 | **Install Shizuku** | If you do not have it, Benimaru Tool will prompt you to safely download it from the official GitHub repository. |
| 2 | **Activate Shizuku** | Start the Shizuku service via **Wireless Debugging (Android 11+)** or via ADB from a computer. [Read the official Shizuku setup guide here](https://shizuku.rikka.app/guide/setup/). |
| 3 | **Grant Overlay Permissions** | To use the Floating Hardware Monitor, Crosshair Overlay, Auto Click & Swipe button, and Floating Menu, grant the *"Display over other apps"* permission when prompted. |

---

## 🚀 Installation

| Step | Action |
|:----:|:-------|
| 1 | Navigate to the [Releases](../../releases) page. |
| 2 | Download the latest `BenimaruTool-vX.X.apk`. |
| 3 | Install the APK on your Android device. |
| 4 | Ensure the **Shizuku** app is installed and actively running in the background. |
| 5 | Open Benimaru Tool and tap **Allow** when prompted for Shizuku access. |
| 6 | Grant the **"Display over other apps"** permission to enable floating services. |
| 7 | Start tweaking! 🎉 |

---

## 🛠️ Built With

| Technology | Purpose |
|:-----------|:--------|
| **[Kotlin](https://kotlinlang.org/)** | 100% native Android development. |
| **[Material 3](https://m3.material.io/)** | Modern, responsive UI utilizing `MaterialCardView` for dynamic themes and programmatically generated dialogs. |
| **[Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)** | Ensuring smooth, non-blocking background task execution and live script output. |
| **[Shizuku API](https://github.com/RikkaApps/Shizuku-API)** | The magic behind executing elevated ADB commands without root. |
| **[ML Kit Translate](https://developers.google.com/ml-kit/language/translation)** | On-device, offline translation for the automatic language feature. |
| **[Toasty](https://github.com/GrenderG/Toasty)** | Clean, stylized success and error popups. |

---

## ⚠️ Disclaimer

> [!WARNING]
> **Use at your own risk.**
> While Benimaru Tool is built with safety fail-safes (such as the 6-second resolution revert timer), forcing unsupported refresh rates, extreme resolutions, or disabling thermal limits on certain hardware configurations can cause system instability, overheating, or UI glitches. The developer is not responsible for bricked devices, bootloops, or hardware damage.
>
> **Shell Execute** runs scripts with Shizuku shell privileges and can change system settings. Only run scripts you trust and understand.
>
> **Flag Secure** depends on your ROM. Android only honours it on debuggable builds, so on stock production ROMs protected apps may still appear black. Don't use it to capture content you aren't allowed to copy.
> 
> **Auto Click & Auto Swipe** automates screen input. Some games detect automated input and may penalize or ban accounts, so use it only where the game's rules allow it.
> 

---

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

<div align="center">


If you like Benimaru Tool, make sure to **star this repo** — it helps the project grow and keeps the updates coming!

</div>
