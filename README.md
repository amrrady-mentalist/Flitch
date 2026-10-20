# ImageSwap

Android app (Kotlin + Jetpack Compose). Sends an email with a blank "live image" link,
then swaps that image from your phone using swipe codes on a black screen.

Needs your Cloudflare Worker (see worker.js) and FairEmail on the phone.

## Folder layout on the phone
Pick one root folder in Settings. Each sub-folder is one effect:

    Magic/
      Cards/      2H.jpg, 3H.jpg, ... (all possible images for that effect)
      Numbers/    1.jpg, 2.jpg, ...

## Build
Push this folder to a GitHub repo. Actions -> "Build APK" -> download the artifact.
