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

class ExportCabrilloManager(private val activity: MainActivity) {

    private lateinit var createFileLauncher: ActivityResultLauncher<Intent>
    private var contestStartTime: String = ""

    fun registrarExportador(callback: (Uri?) -> Unit) {
        createFileLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri: Uri? = result.data?.data
                if (uri != null && contestStartTime.isNotEmpty()) {
                    val conteudo = gerarConteudoCabrillo(contestStartTime)
                    if (conteudo.isNotEmpty()) {
                        salvarCabrillo(uri, conteudo)
                    }
                }
            }
        }
    }

    fun iniciarExportacaoCabrillo(startTime: String) {
        this.contestStartTime = startTime

        // Nome de arquivo temporário — será substituído corretamente dentro de gerarConteudoCabrillo
        val nomeTemporario = "contest_export.log"

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, nomeTemporario)
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
            builder.appendLine("CREATED-BY: QRU v1.0")
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
                    (freqRaw.replace(".", "").take(5).toInt()).toString()
                } catch (_: Exception) {
                    "00000"
                }

                val modo = mapearModoParaCabrillo(qsoCursor.getString(2))
                val call = qsoCursor.getString(3)
                val srst = qsoCursor.getString(4).padEnd(3)
                val snum = qsoCursor.getInt(5).toString().padStart(3, '0')
                val sexch = (qsoCursor.getString(6) ?: "").padEnd(6)
                val rrst = qsoCursor.getString(7).padEnd(3)
                val rnum = qsoCursor.getInt(8).toString().padStart(3, '0')
                val rexch = (qsoCursor.getString(9) ?: "").padEnd(6)

                builder.appendLine("QSO: $freq $modo $date $time $userCall $srst $snum $sexch $call $rrst $rnum $rexch")

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
