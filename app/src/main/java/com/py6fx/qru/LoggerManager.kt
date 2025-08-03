package com.py6fx.qru

import android.database.sqlite.SQLiteDatabase
import android.widget.EditText
import android.widget.TextView
import java.io.File
import android.widget.Toast

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
    fun logQSO(activity: MainActivity) {
        val userCall = activity.findViewById<TextView>(R.id.user_indicator).text.toString().trim()
        val contestName = activity.findViewById<TextView>(R.id.contest_indicator).text.toString().trim()

        if (userCall.isEmpty() || userCall == "USER?") {
            Toast.makeText(activity, "No active user selected.", Toast.LENGTH_LONG).show()
            return
        }
        if (contestName.isEmpty() || contestName == "CONTEST?") {
            Toast.makeText(activity, "No contest selected.", Toast.LENGTH_LONG).show()
            return
        }

        val dbPath = File(activity.filesDir, "db/$userCall.db")
        if (!dbPath.exists()) {
            Toast.makeText(activity, "Database not found for user $userCall", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READWRITE)

            // Buscar contest_id
            val contestCursor = db.rawQuery(
                "SELECT id FROM Contest WHERE DisplayName = ? ORDER BY datetime(StartTime) DESC LIMIT 1",
                arrayOf(contestName)
            )
            if (!contestCursor.moveToFirst()) {
                Toast.makeText(activity, "Contest ID not found.", Toast.LENGTH_LONG).show()
                contestCursor.close()
                db.close()
                return
            }
            val contestId = contestCursor.getInt(0)
            contestCursor.close()

            // Captura de campos do logger
            val rxCall = activity.findViewById<EditText>(R.id.editText_RX_Call).text.toString().trim().uppercase()
            val qrg = activity.findViewById<TextView>(R.id.qrg_indicator).text.toString().trim()
            val modo = activity.findViewById<TextView>(R.id.mode_indicator).text.toString().trim()
            val rxRST = activity.findViewById<EditText>(R.id.editText_RX_RST).text.toString().trim()
            val txRST = activity.findViewById<EditText>(R.id.editText_TX_RST).text.toString().trim()
            val rxNr = activity.findViewById<EditText>(R.id.editText_RX_Nr).text.toString().trim()
            val rxExch = activity.findViewById<EditText>(R.id.editText_RX_Exch).text.toString().trim()
            val txNr = activity.findViewById<EditText>(R.id.editText_TX_Nr).text.toString().trim()
            val txExch = activity.findViewById<EditText>(R.id.editText_TX_Exch).text.toString().trim()

            // Validação mínima
            if (rxCall.isEmpty() || qrg.isEmpty() || modo.isEmpty() || rxRST.isEmpty() || txRST.isEmpty()) {
                Toast.makeText(activity, "Missing required fields (Call, QRG, Mode, RSTs)", Toast.LENGTH_LONG).show()
                db.close()
                return
            }

            // Conversão segura da QRG para float (remover pontos opcionais)
            //val qrgNumerica = qrg.replace(".", "").toFloatOrNull()?.div(100f) ?: 0f

            val insertQuery = """
            INSERT INTO QSOS (
                contest_id, call, freq, mode, sent_rst, rcvd_rst,
                sent_serial, rcvd_serial, sent_exchange, rcvd_exchange
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

            db.execSQL(
                insertQuery,
                arrayOf(
                    contestId,
                    rxCall,
                    qrg,
                    modo,
                    txRST,
                    rxRST,
                    txNr.toIntOrNull(),
                    rxNr.toIntOrNull(),
                    txExch.ifEmpty { null },
                    rxExch.ifEmpty { null }
                )
            )

            db.close()
            Toast.makeText(activity, "QSO logged successfully!", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(activity, "Error logging QSO: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    fun limparCamposQSO(activity: MainActivity) {
        activity.findViewById<EditText>(R.id.editText_RX_Call).setText("")
        activity.findViewById<EditText>(R.id.editText_RX_Nr).setText("")
        activity.findViewById<EditText>(R.id.editText_RX_Exch).setText("")
        activity.findViewById<EditText>(R.id.editText_TX_Exch).setText("")
    }
    fun obterQsosDoContestAtual(activity: MainActivity): List<QsoLogItem> {
        val lista = mutableListOf<QsoLogItem>()

        val userCall = activity.findViewById<TextView>(R.id.user_indicator).text.toString().trim()
        val contestName = activity.findViewById<TextView>(R.id.contest_indicator).text.toString().trim()
        val dbPath = File(activity.filesDir, "db/$userCall.db")
        if (!dbPath.exists() || userCall.isEmpty() || contestName.isEmpty()) return lista

        try {
            val db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READONLY)
            // Pega o contest_id do contest ativo
            val contestCursor = db.rawQuery(
                "SELECT id FROM Contest WHERE DisplayName = ? ORDER BY datetime(StartTime) DESC LIMIT 1",
                arrayOf(contestName)
            )
            if (!contestCursor.moveToFirst()) {
                contestCursor.close()
                db.close()
                return lista
            }
            val contestId = contestCursor.getInt(0)
            contestCursor.close()

            // Busca todos os QSOs desse contest, ordenando por timestamp
            val qsoCursor = db.rawQuery(
                "SELECT timestamp, freq, call, rcvd_rst, rcvd_serial, rcvd_exchange, sent_rst, sent_serial, sent_exchange " +
                        "FROM QSOS WHERE contest_id = ? ORDER BY datetime(timestamp) DESC",
                arrayOf(contestId.toString())
            )
            if (qsoCursor.moveToFirst()) {
                do {
                    val timestamp = qsoCursor.getString(0) ?: ""
                    val qrg = qsoCursor.getString(1) ?: ""
                    val rxCall = qsoCursor.getString(2) ?: ""
                    val rxRst = qsoCursor.getString(3) ?: ""
                    val rxNr = qsoCursor.getInt(4).toString()
                    val rxExch = qsoCursor.getString(5) ?: ""
                    val txRst = qsoCursor.getString(6) ?: ""
                    val txNr = qsoCursor.getInt(7).toString()
                    val txExch = qsoCursor.getString(8) ?: ""

                    lista.add(
                        QsoLogItem(
                            timestamp, qrg, rxCall, rxRst, rxNr, rxExch, txRst, txNr, txExch
                        )
                    )
                } while (qsoCursor.moveToNext())
            }
            qsoCursor.close()
            db.close()
        } catch (_: Exception) { }
        return lista
    }

}

