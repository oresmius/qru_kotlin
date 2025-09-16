# QRU – FT-817(ND) Bluetooth CAT Logger (Developing)

Hi! PY6FX / PY1XR - Fábio, here;

**QRU** is a very simple, minimalistic, open-source Android contest logger app designed for the Yaesu FT-817(ND), developed with the help of Recruit 0101, my Chat GPT assistant.  
It allows portable ham radio operators to log QSOs in the field with automatic capture of frequency, mode, and timestamp via CAT over Bluetooth.
This project also aims to put into practice what I learned in my degree in Data Analysis.

---

## Features

- **Direct CAT Bluetooth Integration:**  
  Connects to your FT-817(ND) via a Bluetooth serial adapter and reads frequency and mode.

- **No more manual logging:**  
  Automatic data entry reduces errors and hassle during portable operations and contests.

- **Local Database:**  
  Each user has an independent SQLite database for logs, making backup and management simple.

- **Minimal, Field-ready UI:**  
  All essential controls and info in one fixed layout — no unnecessary menus or clutter.

- **Contest Management:**  
  Create, edit, resume, export, and delete contests per user.

- **.UDC Files**
  
  QRU can import N1MM Logger+ .udc contest definition files that you provide.

- **Open Source:**  
  Project is fully open for study and community contributions. Commercial use is prohibited.

---

## Notes

- The QRU is exclusively a logger, so you can't control the FT-817(ND) with it, as CAT communication is used only for reading the QRG and radio mode.
- The application is being developed exclusively for portable operations in contests. You can use a "general log" for POTA, SOTA, and other portable operations that are not contests, but that is not the purpose.
- The FT 818, FT 857 and FT 897 radios have practically the same CAT protocol as the FT 817(ND); it is very likely that QRU will work with them, but I have never tested it.
- This logger is designed for fun and DX practice in contests during portable operations, no fierce competition. If you need lightning-fast operation, with CW keyers and SSB voice recording like the N1MM, the QRU certainly won't do.
- I really recommend that in the field, you use a cell phone just for the logger, it can be an old one that is still compatible, that you no longer use.
- QRU is designed for phones with at least a 5-inch screen. Anything smaller may not work. It's also not designed for tablets; it may not look good.

---

## THE AUXILIARY MEMORYS

Although the app is quite intuitive (at least I tried), the auxiliary memory system (the field just above the RX Call field) can be a bit confusing at first.

Yellow call: The last QSO made in the current QRG and mode. This is a dupe.

Green call: This is an auxiliary memory created by the MEM button and can be created in any situation, even over a dupe.

---

## How to Build

1. Clone the repository:
   ```bash
  git clone https://github.com/oresmius/qru_kotlin

2. Open in Android Studio (Kotlin, minSdk 25).

3. Build and install on your device.

4. Pair your FT-817ND with a Bluetooth serial adapter.

5. Select the device in the app and start logging.

## How to simply install

1. Download the .apk in releases
2. Install on your Android (>= 7.1) phone. Remember, by default, Android can refuse to install third-party .apks; you need to grant permission.

## Requirements

  Android (>= 7.1) device with Bluetooth.

  Yaesu FT-817(ND) radio.

  Bluetooth serial adapter configured for CAT port.

  Proper Bluetooth pairing.

## Usage

    New User: Register your callsign and info.

    Create/Resume Contest: Set up your contest environment.

    Bluetooth: Connect to your FT-817ND and start logging.

    Logger: Log QSOs with automatic frequency, mode, and time capture.

    Export: Export logs in Cabrillo or ADIF.

--------------------------------------------------------------------

QRU - Amateur Radio Contest Logger
Copyright (C) 2025 Fabio Sousa (PY6FX)

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.

----------------------------------------------------------------------
Disclaimer:

QRU does not include or redistribute any `.udc` files.  
N1MM Logger+ and related marks are trademarks of their respective owners.  
References to “N1MM Logger+” are for compatibility description only;  
there is no affiliation, partnership, or endorsement.  

----------------------------------------------------------------------

Full text of the GNU General Public License v3:
<https://www.gnu.org/licenses/gpl-3.0.txt>

----------------------------------------------------------------------
PRs and suggestions are welcome!
Please open issues for bugs or ideas, but keep in mind the minimalist, single-radio focus of this app.

Author

  Fábio Almeida e Sousa [PY6FX] — [py1xr.qsl@gmail.com]

  Recruit [0101] — My Chat GPT Assistant

  73
