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

- **Open Source:**  
  Project is fully open for study and community contributions. Commercial use is prohibited.

---

## Notes

- The QRU is exclusively a logger, so you can't control the FT-817(ND) with it, as CAT communication is used only for reading the QRG and radio mode.
- The application is being developed exclusively for portable operations in contests. You can use a "general log" for POTA, SOTA, and other portable operations that are not contests, but that is not the purpose.
- The FT 818, FT 857 and FT 897 radios have practically the same CAT protocol as the FT 817(ND); it is very likely that QRU will work with them, but I have never tested it.
- This logger is designed for fun and DX practice in contests during portable operations no fierce competition. If you need lightning-fast operation, with CW keyers and SSB voice recording like the N1MM, the QRU certainly won't do.

---

## How to Build

1. Clone the repository:
   ```bash
  git clone https://github.com/oresmius/qru_kotlin

2. Open in Android Studio (Kotlin, minSdk 21).

3. Build and install on your device.

4. Pair your FT-817ND with a Bluetooth serial adapter.

5. Select the device in the app and start logging.


## Requirements

  Android device with Bluetooth.

  Yaesu FT-817(ND) radio.

  Bluetooth serial adapter configured for CAT port.

  Proper Bluetooth pairing.

## Usage

    New User: Register your callsign and info.

    Create/Resume Contest: Set up your contest environment.

    Bluetooth: Connect to your FT-817ND and start logging.

    Logger: Log QSOs with automatic frequency, mode, and time capture.

    Export: Export logs in Cabrillo or ADIF (coming soon).

----------------------

Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)

Copyright (c) 2025 Fábio Sousa [PY6FX]

This work is licensed under the Creative Commons Attribution-NonCommercial 4.0 International License.
To view a copy of this license, visit http://creativecommons.org/licenses/by-nc/4.0/
or send a letter to Creative Commons, PO Box 1866, Mountain View, CA 94042, USA.

You are free to:
- Share — copy and redistribute the material in any medium or format
- Adapt — remix, transform, and build upon the material

Under the following terms:
- Attribution — You must give appropriate credit, provide a link to the license, and indicate if changes were made.
- NonCommercial — You may not use the material for commercial purposes.

No additional restrictions — You may not apply legal terms or technological measures that legally restrict others from doing anything the license permits.

Disclaimer

This software is provided "as is", without warranty of any kind, express or implied, including but not limited to the warranties of merchantability, fitness for a particular purpose and noninfringement.
The author shall not be liable for any claim, damages, or other liability, whether in an action of contract, tort or otherwise, arising from, out of or in connection with the software or the use or other dealings in the software.
Use at your own risk.
Contributing

-----------------------

PRs and suggestions are welcome!
Please open issues for bugs or ideas, but keep in mind the minimalist, single-radio focus of this app.

Author

  Fábio Sousa [PY6FX] — [py1xr.qsl@gmail.com]

  Recruit [0101] — My Chat GPT Assistant

  73

