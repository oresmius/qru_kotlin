package com.py6fx.qru

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import java.io.OutputStream
import android.database.sqlite.SQLiteDatabase
import android.widget.TextView
import java.io.File

class ExportCabrilloManager(private val activity: MainActivity) {

    private lateinit var createFileLauncher: ActivityResultLauncher<Intent>
    private var contestParaExportar: String = ""

    fun registrarExportador(callback: (Uri?) -> Unit) {
        createFileLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri: Uri? = result.data?.data
                if (uri != null && contestParaExportar.isNotEmpty()) {
                    val conteudo = gerarConteudoCabrillo(contestParaExportar)
                    if (conteudo.isNotEmpty()) {
                        salvarCabrillo(uri, conteudo)
                    }
                }
            }
        }
    }

    fun iniciarExportacaoCabrillo(contestDisplayName: String, nomeSugerido: String = "contest.log") {
        this.contestParaExportar = contestDisplayName

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, nomeSugerido)
        }
        createFileLauncher.launch(intent)
    }

    fun salvarCabrillo(uri: Uri, conteudo: String) {
        try {
            val outputStream: OutputStream? = activity.contentResolver.openOutputStream(uri)
            outputStream?.bufferedWriter()?.use { writer ->
                writer.write(conteudo)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun gerarConteudoCabrillo(contestDisplayName: String): String {
        val userCall = activity.findViewById<TextView>(R.id.user_indicator).text.toString().trim()
        if (userCall.isEmpty() || contestDisplayName.isEmpty()) return ""

        val dbPath = File(activity.filesDir, "db/$userCall.db")
        if (!dbPath.exists()) return ""

        val builder = StringBuilder()

        try {
            val db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READONLY)

            val contestCursor = db.rawQuery("SELECT * FROM Contest WHERE DisplayName = ? ORDER BY datetime(StartTime) DESC LIMIT 1", arrayOf(contestDisplayName))
            if (!contestCursor.moveToFirst()) {
                contestCursor.close()
                db.close()
                return ""
            }

            val contestId = contestCursor.getInt(contestCursor.getColumnIndexOrThrow("id"))
            val modeRaw = contestCursor.getString(contestCursor.getColumnIndexOrThrow("Mode"))
            val cabrilloMode = mapearModoParaCabrillo(modeRaw)
            val sendExchange = contestCursor.getString(contestCursor.getColumnIndexOrThrow("SendExchange"))
            val category = contestCursor.getString(contestCursor.getColumnIndexOrThrow("Operator"))
            val band = contestCursor.getString(contestCursor.getColumnIndexOrThrow("Band"))

            builder.appendLine("START-OF-LOG: 3.0")
            builder.appendLine("CALLSIGN: $userCall")
            builder.appendLine("CONTEST: $contestDisplayName")
            builder.appendLine("CATEGORY-OPERATOR: $category")
            builder.appendLine("CATEGORY-BAND: $band")
            builder.appendLine("CATEGORY-MODE: $cabrilloMode")
            builder.appendLine("CATEGORY-TRANSMITTER: ONE")
            builder.appendLine("CATEGORY-POWER: LOW")
            builder.appendLine("CATEGORY-ASSISTED: NON-ASSISTED")
            builder.appendLine("OPERATORS: $userCall")
            builder.appendLine("CLUB: UNKNOWN")
            builder.appendLine("CREATED-BY: QRU v1.0")
            builder.appendLine()

            contestCursor.close()

            val qsoCursor = db.rawQuery("SELECT timestamp, freq, mode, call, sent_rst, sent_serial, sent_exchange, rcvd_rst, rcvd_serial, rcvd_exchange FROM QSOS WHERE contest_id = ? ORDER BY datetime(timestamp) ASC", arrayOf(contestId.toString()))
            while (qsoCursor.moveToNext()) {
                val ts = qsoCursor.getString(0).replace(":", "")
                val freq = qsoCursor.getString(1)
                val modo = mapearModoParaCabrillo(qsoCursor.getString(2))
                val call = qsoCursor.getString(3)
                val srst = qsoCursor.getString(4).padEnd(3)
                val snum = qsoCursor.getInt(5).toString().padStart(3, '0')
                val sexch = (qsoCursor.getString(6) ?: "").padEnd(6)
                val rrst = qsoCursor.getString(7).padEnd(3)
                val rnum = qsoCursor.getInt(8).toString().padStart(3, '0')
                val rexch = (qsoCursor.getString(9) ?: "").padEnd(6)

                builder.appendLine("QSO: $freq $modo ${ts.substring(0,10)} ${ts.substring(11,15)} $userCall $srst $snum $sexch $call $rrst $rnum $rexch")
            }
            qsoCursor.close()
            db.close()

            builder.appendLine("END-OF-LOG:")
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }

        return builder.toString()
    }

    companion object {
        fun mapearModoParaCabrillo(modo: String): String {
            return when (modo.uppercase()) {
                "LSB", "USB" -> "PH"
                "CW", "CWR" -> "CW"
                "FM" -> "FM"
                "DIG", "PKT", "FT8", "PSK", "JT65" -> "DG"
                "RTTY" -> "RY"
                else -> "PH"
            }
        }
    }
}
