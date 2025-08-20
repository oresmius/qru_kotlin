package com.py6fx.qru

import android.database.sqlite.SQLiteDatabase
import android.widget.EditText
import android.widget.TextView
import java.io.File
import android.widget.Toast
import androidx.core.content.ContextCompat

class LoggerManager {
    // --- Pré-log: memórias voláteis (sessão atual) ---
    private data class MemoryQSO(
        val freqKHz: Double,
        val rxCall: String,
        val rxRst: String?,
        val txRst: String?,
        val rxNr: String?,
        val rxExch: String?,
        val txExch: String?
    )

    private val memories = mutableListOf<MemoryQSO>()
    private val MEM_TOL_KHZ = 2.5

    // Converte "7.074.00" -> 7074.00 kHz
    private fun qrgStringToKHz(qrgStr: String): Double? {
        val num = qrgStr.replace(".", "").toDoubleOrNull() ?: return null
        return num / 100.0
    }

    private fun findMemoryNear(freqKHz: Double): MemoryQSO? =
        memories.minByOrNull { kotlin.math.abs(it.freqKHz - freqKHz) }
            ?.takeIf { kotlin.math.abs(it.freqKHz - freqKHz) <= MEM_TOL_KHZ }

    private fun upsertMemoryAt(freqKHz: Double, m: MemoryQSO) {
        val idx = memories.indexOfFirst { kotlin.math.abs(it.freqKHz - freqKHz) <= MEM_TOL_KHZ }
        if (idx >= 0) memories[idx] = m else memories.add(m)
    }


    companion object {
        var isEditing: Boolean = false
            private set
        private var editingQsoId: Int? = null
    }

    fun RSTAutomatico(modo: String, editTextTX: EditText, editTextRX: EditText) {
        val modosFonia = setOf("LSB", "USB", "FM", "AM")
        val rst = if (modosFonia.contains(modo.uppercase())) "59" else "599"
        editTextTX.setText(rst)
        editTextRX.setText(rst)
    }

    fun preencherTXExch(activity: MainActivity) {
        val userCall = activity.findViewById<TextView>(R.id.user_indicator).text.toString().trim()
        val contestName = activity.findViewById<TextView>(R.id.contest_indicator).text.toString().trim()
        val editTextTxNr = activity.findViewById<EditText>(R.id.editText_TX_Nr)
        val editTextTxExch = activity.findViewById<EditText>(R.id.editText_TX_Exch)

        if (userCall.isEmpty() || userCall == "USER?") return
        if (contestName.isEmpty() || contestName == "CONTEST?") return

        val dbPath = File(activity.filesDir, "db/$userCall.db")
        if (!dbPath.exists()) return

        try {
            val db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READONLY)

            val cursor = db.rawQuery(
                "SELECT id, SendExchange FROM Contest WHERE DisplayName = ? ORDER BY datetime(StartTime) DESC LIMIT 1",
                arrayOf(contestName)
            )

            if (cursor.moveToFirst()) {
                val contestId = cursor.getInt(0)
                val sendExch = cursor.getString(1).trim()

                if (sendExch == "#") {
                    val qsoCursor = db.rawQuery(
                        "SELECT COUNT(*) FROM QSOS WHERE contest_id = ?",
                        arrayOf(contestId.toString())
                    )
                    val count = if (qsoCursor.moveToFirst()) qsoCursor.getInt(0) else 0
                    val nextSerial = count + 1

                    editTextTxNr.setText(nextSerial.toString())
                    editTextTxNr.isEnabled = false
                    editTextTxExch.setText("")
                    editTextTxExch.isEnabled = false
                    qsoCursor.close()
                } else {
                    editTextTxExch.setText(sendExch)
                    editTextTxExch.isEnabled = false
                    editTextTxNr.setText("")
                    editTextTxNr.isEnabled = false
                }
            }

            cursor.close()
            db.close()
        } catch (_: Exception) {}
    }

    fun logQSO(activity: MainActivity) {
        val userCall = activity.findViewById<TextView>(R.id.user_indicator).text.toString().trim()
        val contestName = activity.findViewById<TextView>(R.id.contest_indicator).text.toString().trim()

        if (userCall.isEmpty() || userCall == "USER?" || contestName.isEmpty() || contestName == "CONTEST?") {
            Toast.makeText(activity, "User or contest not selected.", Toast.LENGTH_LONG).show()
            return
        }

        val dbPath = File(activity.filesDir, "db/$userCall.db")
        if (!dbPath.exists()) {
            Toast.makeText(activity, "Database not found for user $userCall", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READWRITE)

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

            val rxCall = activity.findViewById<EditText>(R.id.editText_RX_Call).text.toString().trim().uppercase()
            val qrg = activity.findViewById<TextView>(R.id.qrg_indicator).text.toString().trim()
            val modo = activity.findViewById<TextView>(R.id.mode_indicator).text.toString().trim()
            val rxRST = activity.findViewById<EditText>(R.id.editText_RX_RST).text.toString().trim()
            val txRST = activity.findViewById<EditText>(R.id.editText_TX_RST).text.toString().trim()
            val rxNr = activity.findViewById<EditText>(R.id.editText_RX_Nr).text.toString().trim()
            val rxExch = activity.findViewById<EditText>(R.id.editText_RX_Exch).text.toString().trim()
            val txNr = activity.findViewById<EditText>(R.id.editText_TX_Nr).text.toString().trim()
            val txExch = activity.findViewById<EditText>(R.id.editText_TX_Exch).text.toString().trim()

            if (rxCall.isEmpty() || qrg.isEmpty() || modo.isEmpty() || rxRST.isEmpty() || txRST.isEmpty()) {
                Toast.makeText(activity, "Missing required fields (Call, QRG, Mode, RSTs)", Toast.LENGTH_LONG).show()
                db.close()
                return
            }

            val insertQuery = """
                INSERT INTO QSOS (
                    contest_id, call, freq, mode, sent_rst, rcvd_rst,
                    sent_serial, rcvd_serial, sent_exchange, rcvd_exchange
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            db.execSQL(
                insertQuery,
                arrayOf(
                    contestId, rxCall, qrg, modo, txRST, rxRST,
                    txNr.toIntOrNull(), rxNr.toIntOrNull(),
                    txExch.ifEmpty { null }, rxExch.ifEmpty { null }
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
        activity.findViewById<TextView>(R.id.textView_log_memory).text = ""
    }

    fun obterQsosDoContestAtual(activity: MainActivity): List<QsoLogItem> {
        val lista = mutableListOf<QsoLogItem>()
        val userCall = activity.findViewById<TextView>(R.id.user_indicator).text.toString().trim()
        val contestName = activity.findViewById<TextView>(R.id.contest_indicator).text.toString().trim()
        val dbPath = File(activity.filesDir, "db/$userCall.db")
        if (!dbPath.exists() || userCall.isEmpty() || contestName.isEmpty()) return lista

        try {
            val db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READONLY)
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

            val qsoCursor = db.rawQuery(
                "SELECT id, timestamp, freq, mode, call, rcvd_rst, rcvd_serial, rcvd_exchange, sent_rst, sent_serial, sent_exchange " +
                        "FROM QSOS WHERE contest_id = ? ORDER BY datetime(timestamp) DESC",
                arrayOf(contestId.toString())
            )
            if (qsoCursor.moveToFirst()) {
                do {
                    val id = qsoCursor.getInt(0)
                    val timestamp = qsoCursor.getString(1) ?: ""
                    val qrg = qsoCursor.getString(2) ?: ""
                    val mode = qsoCursor.getString(3) ?: ""
                    val rxCall = qsoCursor.getString(4) ?: ""
                    val rxRst = qsoCursor.getString(5) ?: ""
                    val rxNr = qsoCursor.getInt(6).toString()
                    val rxExch = qsoCursor.getString(7) ?: ""
                    val txRst = qsoCursor.getString(8) ?: ""
                    val txNr = qsoCursor.getInt(9).toString()
                    val txExch = qsoCursor.getString(10) ?: ""

                    lista.add(QsoLogItem(id, timestamp, qrg, mode, rxCall, rxRst, rxNr, rxExch, txRst, txNr, txExch))
                } while (qsoCursor.moveToNext())
            }
            qsoCursor.close()
            db.close()
        } catch (_: Exception) {}
        return lista
    }

    fun entrarEditMode(
        activity: MainActivity,
        qsoId: Int,
        rxCall: String,
        rxRst: String,
        rxNr: String?,
        rxExch: String?,
        txRst: String,
        txExch: String?
    ) {
        activity.findViewById<EditText>(R.id.editText_RX_Call).setText(rxCall)
        activity.findViewById<EditText>(R.id.editText_RX_RST).setText(rxRst)
        activity.findViewById<EditText>(R.id.editText_RX_Nr).setText(rxNr ?: "")
        activity.findViewById<EditText>(R.id.editText_RX_Exch).setText(rxExch ?: "")
        activity.findViewById<EditText>(R.id.editText_TX_RST).setText(txRst)
        activity.findViewById<EditText>(R.id.editText_TX_Exch).isEnabled = false
        activity.findViewById<EditText>(R.id.editText_TX_Nr).isEnabled = false

        isEditing = true
        editingQsoId = qsoId

        setEditModeUI(activity, true)

        Toast.makeText(activity, "Editing QSO #$qsoId – press “Up. QSO” to save.", Toast.LENGTH_LONG).show()
    }

    fun cancelarEdicao(activity: MainActivity) {
        if (!isEditing) return
        limparCamposQSO(activity)
        sairDoEditMode(activity)
        Toast.makeText(activity, "Edition canceled.", Toast.LENGTH_LONG).show()
    }

    fun atualizarQSO(activity: MainActivity): Boolean {
        val qsoId = editingQsoId
        if (!isEditing || qsoId == null) {
            Toast.makeText(activity, "No QSO in Edit Mode.", Toast.LENGTH_LONG).show()
            return false
        }

        val userCall = activity.findViewById<TextView>(R.id.user_indicator).text.toString().trim()
        if (userCall.isEmpty() || userCall == "USER?") {
            Toast.makeText(activity, "No active user selected.", Toast.LENGTH_LONG).show()
            return false
        }

        val dbPath = File(activity.filesDir, "db/$userCall.db")
        if (!dbPath.exists()) {
            Toast.makeText(activity, "Database not found for user $userCall", Toast.LENGTH_LONG).show()
            return false
        }

        val rxCall = activity.findViewById<EditText>(R.id.editText_RX_Call).text.toString().trim().uppercase()
        val rxRST = activity.findViewById<EditText>(R.id.editText_RX_RST).text.toString().trim()
        val rxNr = activity.findViewById<EditText>(R.id.editText_RX_Nr).text.toString().trim()
        val rxExch = activity.findViewById<EditText>(R.id.editText_RX_Exch).text.toString().trim().uppercase()
        val txRST = activity.findViewById<EditText>(R.id.editText_TX_RST).text.toString().trim()
        val txExch = activity.findViewById<EditText>(R.id.editText_TX_Exch).text.toString().trim().uppercase()

        if (rxCall.isEmpty() || rxRST.isEmpty() || txRST.isEmpty()) {
            Toast.makeText(activity, "Missing required fields (Call, RSTs).", Toast.LENGTH_LONG).show()
            return false
        }

        try {
            val db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READWRITE)

            val updateQuery = """
                UPDATE QSOS SET
                    call = ?, rcvd_rst = ?, rcvd_serial = ?, rcvd_exchange = ?,
                    sent_rst = ?, sent_exchange = ?
                WHERE id = ?
            """.trimIndent()

            db.execSQL(
                updateQuery,
                arrayOf(
                    rxCall, rxRST, rxNr.toIntOrNull(), rxExch.ifEmpty { null },
                    txRST, txExch.ifEmpty { null }, qsoId
                )
            )

            db.close()
            sairDoEditMode(activity)
            limparCamposQSO(activity)
            Toast.makeText(activity, "QSO updated successfully!", Toast.LENGTH_LONG).show()
            return true
        } catch (e: Exception) {
            Toast.makeText(activity, "Error updating QSO: ${e.message}", Toast.LENGTH_LONG).show()
            return false
        }
    }

    private fun sairDoEditMode(activity: MainActivity) {
        isEditing = false
        editingQsoId = null
        activity.findViewById<EditText>(R.id.editText_TX_Nr).isEnabled = true
        preencherTXExch(activity)
        setEditModeUI(activity, false)
    }

    private fun setEditModeUI(activity: MainActivity, enabled: Boolean) {
        val status = activity.findViewById<TextView>(R.id.logger_status)
        val mainLabel = activity.findViewById<TextView>(R.id.label_main_menu)
        val btnLog = activity.findViewById<TextView>(R.id.button_log_QSO)
        val btnCancel = activity.findViewById<TextView>(R.id.button_log_cancel)

        if (enabled) {
            status.text = "EDIT MODE"
            status.backgroundTintList = ContextCompat.getColorStateList(activity, android.R.color.holo_orange_light)
            mainLabel.text = "Cancel Ed."
            btnLog.text = "Up. QSO"
            btnCancel.text = "Cancel Ed."
        } else {
            status.text = "LOG MODE"
            status.backgroundTintList = ContextCompat.getColorStateList(activity, android.R.color.holo_green_light)
            mainLabel.text = "Main Menu"
            btnLog.text = "Log QSO"
            btnCancel.text = "Cancel Logger"
        }
    }
    // Cria/atualiza memória com base nos campos atuais do logger.
    // Regras: RX Call obrigatório; overwrite sem aviso se houver memória na janela ±2,5 kHz.
    // Sinal de criação: escrever RX Call no textView_log_memory.
    fun createOrUpdateMemory(activity: MainActivity) {
        val rxCall = activity.findViewById<EditText>(R.id.editText_RX_Call).text.toString().trim().uppercase()
        if (rxCall.isEmpty()) return  // Sem toasts/avisos; simplesmente não cria.

        val qrgStr = activity.findViewById<TextView>(R.id.qrg_indicator).text.toString().trim()
        val freqKHz = qrgStringToKHz(qrgStr) ?: return

        val rxRst  = activity.findViewById<EditText>(R.id.editText_RX_RST).text.toString().trim().ifEmpty { null }
        val txRst  = activity.findViewById<EditText>(R.id.editText_TX_RST).text.toString().trim().ifEmpty { null }
        val rxNr   = activity.findViewById<EditText>(R.id.editText_RX_Nr).text.toString().trim().ifEmpty { null }
        val rxExch = activity.findViewById<EditText>(R.id.editText_RX_Exch).text.toString().trim().ifEmpty { null }
        val txExch = activity.findViewById<EditText>(R.id.editText_TX_Exch).text.toString().trim().ifEmpty { null }

        val mem = MemoryQSO(freqKHz, rxCall, rxRst, txRst, rxNr, rxExch, txExch)
        upsertMemoryAt(freqKHz, mem)

        // Sinal: exibir apenas o RX Call
        activity.findViewById<TextView>(R.id.textView_log_memory).text = rxCall
    }
    // Aplica a memória próxima da QRG atual nos campos do logger.
    // Campos faltantes na memória apagam o campo correspondente.
    // A memória permanece viva (não é removida).
    fun applyMemoryIfNear(activity: MainActivity) {
        // só faz sentido na tela do logger (pag_8, index 7)
        if (activity.viewFlipper.displayedChild != 7) return

        val qrgStr = activity.findViewById<TextView>(R.id.qrg_indicator).text.toString().trim()
        val freqKHz = qrgStringToKHz(qrgStr) ?: return

        val mem = findMemoryNear(freqKHz) ?: return

        activity.findViewById<EditText>(R.id.editText_RX_Call).setText(mem.rxCall)
        activity.findViewById<EditText>(R.id.editText_RX_RST).setText(mem.rxRst ?: "")
        activity.findViewById<EditText>(R.id.editText_TX_RST).setText(mem.txRst ?: "")
        activity.findViewById<EditText>(R.id.editText_RX_Nr).setText(mem.rxNr ?: "")
        activity.findViewById<EditText>(R.id.editText_RX_Exch).setText(mem.rxExch ?: "")
        activity.findViewById<EditText>(R.id.editText_TX_Exch).setText(mem.txExch ?: "")

        // Mantém a memória; nada de toasts.
    }
    // Atualiza o textView_log_memory com o RX Call da memória mais próxima (≤ 2,5 kHz),
// exibindo somente na página do logger; fora disso, limpa.
    fun updateMemorySuggestionForCurrentQrg(activity: MainActivity) {
        val tv = activity.findViewById<TextView>(R.id.textView_log_memory)

        if (activity.viewFlipper.displayedChild != 7) {
            tv.text = ""
            return
        }

        val qrgStr = activity.findViewById<TextView>(R.id.qrg_indicator).text.toString().trim()
        val freqKHz = qrgStringToKHz(qrgStr)
        if (freqKHz == null) { tv.text = ""; return }

        val mem = findMemoryNear(freqKHz)
        tv.text = mem?.rxCall ?: ""
    }
    // Zera todas as memórias voláteis e limpa a sugestão visual.
    fun clearAllMemories(activity: MainActivity) {
        memories.clear()
        activity.findViewById<TextView>(R.id.textView_log_memory).text = ""
    }
}