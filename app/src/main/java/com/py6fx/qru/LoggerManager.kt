package com.py6fx.qru

import android.database.sqlite.SQLiteDatabase
import android.widget.EditText
import android.widget.TextView
import java.io.File

class LoggerManager {
    fun RSTAutomatico(modo: String, editTextTX: EditText, editTextRX: EditText) {
        val modosFonia = setOf("LSB", "USB", "FM", "AM")
        val rst = if (modosFonia.contains(modo.uppercase())) "59" else "599"
        editTextTX.setText(rst)
        editTextRX.setText(rst)
    }

    fun preencherTXExch(activity: MainActivity) {
        val userCall = activity.findViewById<TextView>(R.id.user_indicator).text.toString().trim()
        val contestName =
            activity.findViewById<TextView>(R.id.contest_indicator).text.toString().trim()

        val editTextTxNr = activity.findViewById<EditText>(R.id.editText_TX_Nr)
        val editTextTxExch = activity.findViewById<EditText>(R.id.editText_TX_Exch)

        if (userCall.isEmpty() || userCall == "USER?") return
        if (contestName.isEmpty() || contestName == "CONTEST?") return

        val dbPath = File(activity.filesDir, "db/$userCall.db")
        if (!dbPath.exists()) return

        try {
            val db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READONLY)

            // Consulta o SendExchange e o contest_id
            val cursor = db.rawQuery(
                "SELECT id, SendExchange FROM Contest WHERE DisplayName = ? ORDER BY datetime(StartTime) DESC LIMIT 1",
                arrayOf(contestName)
            )

            if (cursor.moveToFirst()) {
                val contestId = cursor.getInt(0)
                val sendExch = cursor.getString(1).trim()

                if (sendExch == "#") {
                    // Modo serial automático
                    val qsoCursor = db.rawQuery(
                        "SELECT COUNT(*) FROM QSOS WHERE contest_id = ?",
                        arrayOf(contestId.toString())
                    )
                    val count = if (qsoCursor.moveToFirst()) qsoCursor.getInt(0) else 0
                    val nextSerial = count + 1

                    editTextTxNr.setText(nextSerial.toString())
                    editTextTxNr.isEnabled = false // bloqueia edição

                    editTextTxExch.setText("")
                    editTextTxExch.isEnabled = true // libera caso tenha sido bloqueado antes

                    qsoCursor.close()
                } else {
                    // Exchange fixo
                    editTextTxExch.setText(sendExch)
                    editTextTxExch.isEnabled = true

                    editTextTxNr.setText("")
                    editTextTxNr.isEnabled = false // previne edição incorreta em modo fixo
                }
            }

            cursor.close()
            db.close()
        } catch (_: Exception) {
            // falha silenciosa
        }
    }
}

