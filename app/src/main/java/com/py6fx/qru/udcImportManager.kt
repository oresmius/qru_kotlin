/*
 * QRU - Amateur Radio Contest Logger
 * Copyright (C) 2025 Fabio Almeida e Sousa (PY6FX)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Disclaimer:
 * QRU does not include or redistribute any .udc files.
 * N1MM Logger+ and related marks are trademarks of their respective owners.
 * References to "N1MM Logger+" are for compatibility description only;
 * there is no affiliation, partnership, or endorsement.
 * Users are responsible for ensuring they have the rights to use any files
 * they import. All imports are processed locally on the user's device.
 */

package com.py6fx.qru

import android.content.ContentValues
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import android.database.sqlite.SQLiteDatabase

/**
 * Imports a N1MM .UDC file and inserts (or overwrites) a row in db/main.qru -> Contest table.
 * - Minimal fields required: Name, CabrilloName, Mode
 * - Optional fields mapped if present: DisplayName, DupeType, Multiplier1Name/2/3, Period,
 *   PointsPerContact, MasterDTA, CWMessages, SSBMessages, DigiMessages, CabrilloVersion
 * - Creates a timestamped backup of main.qru before writing
 */
class UdcImportManager(
    private val activity: MainActivity,
    private val onImported: (() -> Unit)? = null
) {

    private lateinit var openUdcLauncher: ActivityResultLauncher<Array<String>>

    fun registerImporter() {
        openUdcLauncher = activity.registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            importFromUri(uri)
        }
    }

    fun startImport() {
        // We can’t filter by extension reliably; allow common text/binary + */*
        openUdcLauncher.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
    }

    // ---------- Core ----------
    private fun importFromUri(uri: Uri) {
        val text = readAllText(uri) ?: run {
            toast("Failed to read the selected file.")
            return
        }

        val kv = parseContestSection(text)

        // Minimal required fields
        val name = kv["Name"]?.trim().orEmpty()
        val cabrillo = kv["CabrilloName"]?.trim().orEmpty()
        val mode = kv["Mode"]?.trim()?.uppercase().orEmpty()

        if (name.isEmpty() || cabrillo.isEmpty() || mode.isEmpty()) {
            toast("Invalid .UDC (Contest section must include Name, CabrilloName, Mode).")
            return
        }

        // Prepare ContentValues with only present keys (so defaults apply on INSERT)
        val cv = ContentValues().apply {
            put("Name", truncate(name, 10))
            putIfPresent(this, "DisplayName", kv["DisplayName"])
            put("CabrilloName", truncate(cabrillo, 15))
            put("Mode", truncate(mode, 6))
            putIfInt(this, "DupeType", kv["DupeType"])
            putIfPresent(this, "Multiplier1Name", kv["Multiplier1Name"])
            putIfPresent(this, "Multiplier2Name", kv["Multiplier2Name"])
            putIfPresent(this, "Multiplier3Name", kv["Multiplier3Name"])
            // Period (inteiro garantido)
            put("Period", parseIntOr(2, kv["Period"]))
            // PointsPerContact (inteiro garantido)
            put("PointsPerContact", parseIntOr(0, kv["PointsPerContact"]))
            putIfPresent(this, "MasterDTA", kv["MasterDTA"])
            putIfPresent(this, "CWMessages", kv["CWMessages"])
            putIfPresent(this, "SSBMessages", kv["SSBMessages"])
            putIfPresent(this, "DigiMessages", kv["DigiMessages"])
            putIfPresent(this, "CabrilloVersion", kv["CabrilloVersion"])
        }

        val mainDbFile = File(activity.filesDir, "db/main.qru")
        if (!mainDbFile.exists()) {
            toast("Main contest database not found (db/main.qru).")
            return
        }

        // Backup before writing
        createBackup(mainDbFile)

        // Upsert logic: if Name or CabrilloName exists -> overwrite; else insert
        try {
            val db = SQLiteDatabase.openDatabase(mainDbFile.path, null, SQLiteDatabase.OPEN_READWRITE)
            db.beginTransaction()
            try {
                val exists = rowExists(db, "Contest", "Name=? OR CabrilloName=?", arrayOf(name, cabrillo))
                if (exists) {
                    // Prefer match by Name; if not found, match by CabrilloName
                    val updatedByName = db.update("Contest", cv, "Name = ? COLLATE NOCASE", arrayOf(name))
                    if (updatedByName == 0) {
                        db.update("Contest", cv, "CabrilloName = ? COLLATE NOCASE", arrayOf(cabrillo))
                    }
                } else {
                    db.insertOrThrow("Contest", null, cv)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
                db.close()
            }

            toast("Contest imported successfully.")
            onImported?.invoke()

        } catch (e: Exception) {
            e.printStackTrace()
            toast("Import failed: ${e.message}")
        }
    }

    // ---------- Helpers ----------
    private fun readAllText(uri: Uri): String? =
        try {
            activity.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use {
                    var t = it.readText()
                    if (t.isNotEmpty() && t[0] == '\uFEFF') t = t.substring(1) // strip BOM
                    t
                }
            }
        } catch (_: Exception) { null }

    /**
     * Very small INI-like parser focusing on the [Contest] section.
     * - Accepts lines like "Key = value"
     * - Ignores comments (#, ;) and non key-value lines
     * - Stops when another [Section] begins
     * - Strips end-of-line ';' comments outside quotes
     */
    private fun parseContestSection(text: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        var inContest = false
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith("#") || line.startsWith(";")) return@forEach

            if (line.startsWith("[") && line.endsWith("]")) {
                inContest = line.equals("[Contest]", ignoreCase = true)
                return@forEach
            }
            if (!inContest) return@forEach

            // key = value
            val eq = line.indexOf('=')
            if (eq <= 0) return@forEach
            val key = line.substring(0, eq).trim()
            val rawVal = line.substring(eq + 1).trim()
            val value = stripInlineComment(rawVal).trim().trim('"')
            if (key.isNotEmpty()) out[key] = value
        }
        return out
    }

    // Remove inline ';' comments only when they are outside quotes
    private fun stripInlineComment(value: String): String {
        var inQuotes = false
        for (i in value.indices) {
            val c = value[i]
            if (c == '"') inQuotes = !inQuotes
            if (!inQuotes && c == ';') {
                return value.substring(0, i).trim()
            }
        }
        return value.trim()
    }

    private fun putIfPresent(cv: ContentValues, column: String, value: String?) {
        val v = value?.trim()
        if (!v.isNullOrEmpty()) cv.put(column, truncate(v, 255))
    }

    private fun putIfInt(cv: ContentValues, column: String, value: String?) {
        val v = value?.trim()
        if (!v.isNullOrEmpty()) v.toIntOrNull()?.let { cv.put(column, it) }
    }

    private fun truncate(s: String, max: Int): String =
        if (s.length <= max) s else s.substring(0, max)

    private fun rowExists(db: SQLiteDatabase, table: String, where: String, args: Array<String>): Boolean {
        db.rawQuery("SELECT 1 FROM $table WHERE $where LIMIT 1", args).use {
            return it.moveToFirst()
        }
    }

    private fun createBackup(dbFile: File) {
        try {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val bak = File(dbFile.parentFile, "main_${stamp}.qru.bak")
            dbFile.copyTo(bak, overwrite = false)
        } catch (_: Exception) {
            // If backup fails we still attempt import (non-fatal), but we keep quiet to avoid noise.
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()

    // Normaliza inteiros: remove aspas simples/duplas e tenta extrair um número
    private fun parseIntOr(default: Int, raw: String?): Int {
        if (raw == null) return default
        val s = raw.trim().trim('"', '\'')
        s.toIntOrNull()?.let { return it }
        // fallback: pega o primeiro inteiro contíguo
        val m = Regex("[-+]?\\d+").find(s)
        return m?.value?.toIntOrNull() ?: default
    }

}
