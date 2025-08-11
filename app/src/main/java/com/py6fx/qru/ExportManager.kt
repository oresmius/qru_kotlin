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
                "SELECT id, DisplayName, CabrilloName, Operator, Operators FROM Contest WHERE StartTime = ? LIMIT 1",
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
            val cabrilloName = contestCursor.getString(contestCursor.getColumnIndexOrThrow("CabrilloName"))
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
                builder.append("<CONTEST_ID:${cabrilloName.length}>$cabrilloName ")
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
        val userCall = activity.findViewById<TextView>(R.id.user_indicator).text.toString().trim()
        if (userCall.isEmpty() || startTime.isEmpty()) return ""

        val dbPath = File(activity.filesDir, "db/$userCall.db")
        if (!dbPath.exists()) return ""

        val builder = StringBuilder()

        try {
            val db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READONLY)

            val contestCursor = db.rawQuery(
                "SELECT * FROM Contest WHERE StartTime = ? LIMIT 1",
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
            val modeRaw = contestCursor.getString(contestCursor.getColumnIndexOrThrow("Mode"))
            val cabrilloMode = mapearModoParaCabrillo(modeRaw)
            val sendExchange = contestCursor.getString(contestCursor.getColumnIndexOrThrow("SendExchange"))
            val category = contestCursor.getString(contestCursor.getColumnIndexOrThrow("Operator"))
            val band = contestCursor.getString(contestCursor.getColumnIndexOrThrow("Band"))

            contestCursor.close()

            // Dados do usuário
            val userCursor = db.rawQuery("SELECT * FROM user WHERE Call = ?", arrayOf(userCall))
            var name = ""
            var address1 = ""
            var address2 = ""
            var address3 = ""
            var email = ""
            var club = ""
            if (userCursor.moveToFirst()) {
                name = userCursor.getString(userCursor.getColumnIndexOrThrow("Name"))
                address1 = userCursor.getString(userCursor.getColumnIndexOrThrow("Address"))
                address2 = "${userCursor.getString(userCursor.getColumnIndexOrThrow("City"))}, ${userCursor.getString(userCursor.getColumnIndexOrThrow("State"))}"
                address3 = userCursor.getString(userCursor.getColumnIndexOrThrow("Country"))
                email = userCursor.getString(userCursor.getColumnIndexOrThrow("Email"))
                club = userCursor.getString(userCursor.getColumnIndexOrThrow("Club"))
            }
            userCursor.close()

            // Cabeçalho Cabrillo
            builder.appendLine("START-OF-LOG: 3.0")
            builder.appendLine("CALLSIGN: $userCall")
            builder.appendLine("CONTEST: $displayName")
            builder.appendLine("CATEGORY-OPERATOR: $category")
            builder.appendLine("CATEGORY-BAND: $band")
            builder.appendLine("CATEGORY-MODE: $cabrilloMode")
            builder.appendLine("CATEGORY-TRANSMITTER: ONE")
            builder.appendLine("CATEGORY-POWER: LOW")
            builder.appendLine("CATEGORY-ASSISTED: NON-ASSISTED")
            builder.appendLine("CLUB: ${club.ifEmpty { "UNKNOWN" }}")
            builder.appendLine("CREATED-BY: QRU v0.3")
            builder.appendLine("EMAIL: ${email.ifEmpty { "unknown@qru.app" }}")
            builder.appendLine("NAME: $name")
            builder.appendLine("ADDRESS: $address1")
            builder.appendLine("ADDRESS: $address2")
            builder.appendLine("ADDRESS: $address3")
            builder.appendLine("OPERATORS: $userCall")
            builder.appendLine("SOAPBOX: Exported automatically by QRU")
            builder.appendLine()

            val qsoCursor = db.rawQuery(
                "SELECT timestamp, freq, mode, call, sent_rst, sent_serial, sent_exchange, rcvd_rst, rcvd_serial, rcvd_exchange FROM QSOS WHERE contest_id = ? ORDER BY datetime(timestamp) ASC",
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
                val date = timestampRaw.substring(0, 10)
                val time = timestampRaw.substring(11, 16).replace(":", "")

                // Frequência: transforma 14.157.72 em 14157
                val freqRaw = qsoCursor.getString(1)
                val freq = try {
                    (freqRaw.replace(".", "").take(5).toInt()).toString().padStart(5)
                } catch (_: Exception) {
                    "00000"
                }

                val modo = mapearModoParaCabrillo(qsoCursor.getString(2))
                val callRx = qsoCursor.getString(3).padEnd(10)
                val callTx = userCall.padEnd(10)
                val srst = qsoCursor.getString(4).padEnd(3)
                val snum = qsoCursor.getInt(5).toString().padStart(4, '0')
                val sexch = (qsoCursor.getString(6) ?: "").padEnd(6)
                val rrst = qsoCursor.getString(7).padEnd(3)
                val rnum = qsoCursor.getInt(8).toString().padStart(4, '0')
                val rexch = (qsoCursor.getString(9) ?: "").padEnd(6)

                builder.appendLine("QSO: $freq $modo $date $time $callTx $srst $snum $sexch $callRx $rrst $rnum $rexch")

            } while (qsoCursor.moveToNext())

            qsoCursor.close()
            db.close()

            builder.appendLine("END-OF-LOG:")

        } catch (e: Exception) {
            Toast.makeText(activity, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
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