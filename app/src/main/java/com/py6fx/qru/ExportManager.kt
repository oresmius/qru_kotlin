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
import android.widget.Toast
import java.io.File
import java.util.Locale

class ExportCabrilloManager(private val activity: MainActivity) {

    private lateinit var createFileLauncher: ActivityResultLauncher<Intent>
    private var contestStartTime: String = ""
    private var exportMode: ExportMode = ExportMode.CABRILLO

    enum class ExportMode {
        CABRILLO, ADIF
    }

    fun registrarExportador(callback: (Uri?) -> Unit) {
        createFileLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri: Uri? = result.data?.data
                if (uri != null && contestStartTime.isNotEmpty()) {
                    val conteudo = when (exportMode) {
                        ExportMode.CABRILLO -> gerarConteudoCabrillo(contestStartTime)
                        ExportMode.ADIF -> gerarConteudoAdif(contestStartTime)
                    }
                    if (conteudo.isNotEmpty()) {
                        salvarCabrillo(uri, conteudo)
                    }
                }
            }
        }
    }

    fun iniciarExportacaoCabrillo(startTime: String) {
        this.contestStartTime = startTime
        this.exportMode = ExportMode.CABRILLO

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, "contest_export.log")
        }
        createFileLauncher.launch(intent)
    }

    fun iniciarExportacaoAdif(startTime: String) {
        this.contestStartTime = startTime
        this.exportMode = ExportMode.ADIF

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, "contest_export.adi")
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

    fun gerarConteudoAdif(startTime: String): String {
        val userCall = activity.findViewById<TextView>(R.id.user_indicator).text.toString().trim()
        if (userCall.isEmpty() || startTime.isEmpty()) return ""

        val dbPath = File(activity.filesDir, "db/$userCall.db")
        if (!dbPath.exists()) return ""

        val builder = StringBuilder()
        builder.appendLine("ADIF Export from QRU")
        builder.appendLine("<ADIF_VER:5>3.1.0")
        builder.appendLine("<PROGRAMID:3>QRU")
        builder.appendLine("<EOH>")

        try {
            val db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READONLY)

            // ATENÇÃO: Carregue Operator (singular) e Operators (plural)
            val contestCursor = db.rawQuery(
                "SELECT id, DisplayName, Operator, Operators FROM Contest WHERE StartTime = ? LIMIT 1",
                arrayOf(startTime)
            )

            if (!contestCursor.moveToFirst()) {
                Toast.makeText(activity, "Contest with StartTime '$startTime' not found!", Toast.LENGTH_LONG).show()
                contestCursor.close()
                db.close()
                return ""
            }

            val contestId = contestCursor.getInt(contestCursor.getColumnIndexOrThrow("id"))
            val displayName = contestCursor.getString(contestCursor.getColumnIndexOrThrow("DisplayName"))
            //val stationCallsignField = contestCursor.getString(contestCursor.getColumnIndexOrThrow("Operator"))?.trim()
            val operatorsField = contestCursor.getString(contestCursor.getColumnIndexOrThrow("Operators"))?.trim()
            contestCursor.close()

            // Proteção contra nulos/vazios
            val stationCallsign = userCall
            val operators = if (operatorsField.isNullOrBlank()) userCall else operatorsField

            val qsoCursor = db.rawQuery(
                "SELECT timestamp, freq, mode, call, sent_rst, rcvd_rst, sent_serial, rcvd_serial, sent_exchange, rcvd_exchange FROM QSOS WHERE contest_id = ? ORDER BY datetime(timestamp) ASC",
                arrayOf(contestId.toString())
            )

            if (!qsoCursor.moveToFirst()) {
                Toast.makeText(activity, "No QSOs found for contest ID $contestId", Toast.LENGTH_LONG).show()
                qsoCursor.close()
                db.close()
                return ""
            }

            do {
                val timestampRaw = qsoCursor.getString(0)
                val date = timestampRaw.substring(0, 10).replace("-", "")
                val time = timestampRaw.substring(11, 16).replace(":", "")

                val freqRaw = qsoCursor.getString(1) ?: "0"
                val freqFormatada = try {
                    val clean = freqRaw.replace(".", "").trimStart('0')
                    val padded = clean.padStart(6, '0')
                    val mhz = padded.substring(0, padded.length - 5)
                    val decimals = padded.substring(padded.length - 5)
                    val freq = "$mhz.$decimals"
                    String.format(Locale.US, "%.5f", freq.toDouble())
                } catch (_: Exception) {
                    "0.00000"
                }

                val mode = qsoCursor.getString(2)
                val call = qsoCursor.getString(3)
                val rstSent = qsoCursor.getString(4)
                val rstRcvd = qsoCursor.getString(5)
                val contestName = displayName

                builder.append("<CALL:${call.length}>$call ")
                builder.append("<QSO_DATE:8>$date ")
                builder.append("<TIME_ON:4>$time ")
                builder.append("<STATION_CALLSIGN:${stationCallsign.length}>$stationCallsign ")
                builder.append("<FREQ:${freqFormatada.length}>$freqFormatada ")
                builder.append("<MODE:${mode.length}>$mode ")
                builder.append("<RST_SENT:${rstSent.length}>$rstSent ")
                builder.append("<RST_RCVD:${rstRcvd.length}>$rstRcvd ")
                builder.append("<OPERATOR:${operators.length}>$operators ")
                builder.append("<APP_LOGGER32_CONTESTNAME:${contestName.length}>$contestName <EOR>\n")

            } while (qsoCursor.moveToNext())

            qsoCursor.close()
            db.close()

        } catch (e: Exception) {
            Toast.makeText(activity, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            return ""
        }

        return builder.toString()
    }

    fun gerarConteudoCabrillo(startTime: String): String {
        return "" // Mantido como está
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
