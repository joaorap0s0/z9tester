# Z9 Tether - Emulator Test Edition

This version is designed to validate the FTP receiver without a Nikon Z9 or Samsung phone.

## What is tested

- Android APK installation and startup
- Foreground FTP receiver service
- FTP control port 2121
- Nikon-style USER/PASS login
- PASV passive FTP negotiation
- Dynamic passive data ports in Nikon's documented range
- JPEG transfer
- MediaStore saving under `Pictures/Z9 Tether`
- Photo counter and latest-photo handling
- Automatic emulator test mode

## Quick emulator test

The app has a **SIMULATE Z9 PHOTO UPLOAD** button. Start the receiver, then press it. The app acts as both the FTP receiver and a simulated Z9 FTP client and uploads a small test JPEG to itself.

For a completely unattended cloud test, push this project to GitHub and run **Z9 Tether Emulator + Simulated Z9 Test** from Actions. The workflow creates an Android 15/API 35 emulator, installs the APK, launches test mode, performs the simulated Nikon FTP upload, and fails unless the app records `PASS`.

## External simulator

`tools/simulated_z9.py` is a standard-library Python FTP client that can be used against a reachable instance of Z9 Tether. It uses the same `nikon` / `z9tether` credentials and uploads a test JPEG.

## Real Z9 test still required

The emulator test validates the Android FTP application. It cannot perfectly reproduce the Samsung S24 Ultra's Wi-Fi hotspot interface. A final real-world test is still needed for S24 hotspot + Z9 Wi-Fi association and the phone's actual hotspot IPv4 address.
