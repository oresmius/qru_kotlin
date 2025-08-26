package com.py6fx.qru

import android.database.sqlite.SQLiteDatabase
import android.widget.EditText
import android.widget.TextView
import java.io.File
import android.widget.Toast
import androidx.core.content.ContextCompat
import android.view.View
import android.widget.Button


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

    // ===== DUPES: Tipos de apoio e tabela de bandas =====
    private data class Band(val name: String, val fLowMHz: Double, val fHighMHz: Double)

    private val bandTable: List<Band> = listOf(
        Band("160m", 1.800, 2.000),
        Band("80m", 3.500, 4.000),
        Band("60m", 5.3515, 5.3665),
        Band("40m", 7.000, 7.300),
        Band("30m", 10.100, 10.150),
        Band("20m", 14.000, 14.350),
        Band("17m", 18.068, 18.168),
        Band("15m", 21.000, 21.450),
        Band("12m", 24.890, 24.990),
        Band("10m", 28.000, 29.700),
        Band("6m", 50.000, 54.000),
        Band("2m", 144.000, 148.000),
        Band("70cm", 430.000, 440.000)
    )

    data class DupeResult(
        val isDupe: Boolean,
        val qsoId: Long? = null,
        val timestampUtc: String? = null,
        val freqMHz: Double? = null,
        val modeStored: String? = null,
        val bandName: String? = null
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
                val sendExch = cursor.getString(1)?.trim().orEmpty()

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
        } catch (_: Exception) {
        }
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

        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READWRITE)

            // ——— Contest ID
            val contestCursor = db.rawQuery(
                "SELECT id FROM Contest WHERE DisplayName = ? ORDER BY datetime(StartTime) DESC LIMIT 1",
                arrayOf(contestName)
            )
            if (!contestCursor.moveToFirst()) {
                Toast.makeText(activity, "Contest ID not found.", Toast.LENGTH_LONG).show()
                contestCursor.close()
                return
            }
            val contestId = contestCursor.getInt(0)
            contestCursor.close()

            // ——— Campos da UI
            val rxCall = activity.findViewById<EditText>(R.id.editText_RX_Call).text.toString().trim().uppercase()
            val qrg    = activity.findViewById<TextView>(R.id.qrg_indicator).text.toString().trim()
            val modo   = activity.findViewById<TextView>(R.id.mode_indicator).text.toString().trim()
            val rxRST  = activity.findViewById<EditText>(R.id.editText_RX_RST).text.toString().trim()
            val txRST  = activity.findViewById<EditText>(R.id.editText_TX_RST).text.toString().trim()
            val rxNr   = activity.findViewById<EditText>(R.id.editText_RX_Nr).text.toString().trim()
            val rxExch = activity.findViewById<EditText>(R.id.editText_RX_Exch).text.toString().trim()
            val txNr   = activity.findViewById<EditText>(R.id.editText_TX_Nr).text.toString().trim()
            val txExch = activity.findViewById<EditText>(R.id.editText_TX_Exch).text.toString().trim()

            if (rxCall.isEmpty() || qrg.isEmpty() || modo.isEmpty() || rxRST.isEmpty() || txRST.isEmpty()) {
                Toast.makeText(activity, "Missing required fields (Call, QRG, Mode, RSTs)", Toast.LENGTH_LONG).show()
                return
            }

            // === CHAVE LÓGICA: se estiver em Edit Mode, não checar DUPE nem exibir banner ===
            if (!LoggerManager.isEditing) {
                // ——— DUPLICATA: checar ANTES do INSERT (lógica já existente)
                val dupe = checkDupeWithBtInterp(
                    db        = db,
                    contestId = contestId.toLong(),
                    callRx    = rxCall,
                    qrgInput  = qrg,     // ex.: "7.074.00"
                    modeRaw   = modo
                )

                // Apenas sinaliza banner; NÃO bloqueia o INSERT
                if (dupe.isDupe) {
                    activity.showDupeBannerFor(dupe.qsoId)
                } else {
                    activity.hideDupeBanner()
                }
            } else {
                // Em Edit Mode: garante banner oculto
                activity.hideDupeBanner()
            }

            // ——— INSERT (inclusive quando for DUPE)
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

            // Mantém o toast de sucesso
            Toast.makeText(activity, "QSO logged successfully!", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(activity, "Error logging QSO: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            db?.close()
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

                    lista.add(
                        QsoLogItem(
                            id, timestamp, qrg, mode, rxCall, rxRst, rxNr, rxExch, txRst, txNr, txExch
                        )
                    )
                } while (qsoCursor.moveToNext())
            }
            qsoCursor.close()
            db.close()
        } catch (_: Exception) {
        }
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
        activity.hideDupeBanner()
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

    fun deleteQSO(activity: MainActivity): Boolean {
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

        return try {
            val db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READWRITE)
            db.execSQL("DELETE FROM QSOS WHERE id = ?", arrayOf(qsoId))
            db.close()

            // Sai do Edit Mode e limpa os campos
            sairDoEditMode(activity)
            limparCamposQSO(activity)

            Toast.makeText(activity, "QSO deleted successfully!", Toast.LENGTH_LONG).show()
            true
        } catch (e: Exception) {
            Toast.makeText(activity, "Error deleting QSO: ${e.message}", Toast.LENGTH_LONG).show()
            false
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

        // referência ao botão de deletar
        val btnDelete = activity.findViewById<Button>(R.id.button_delete_QSO)

        if (enabled) {
            status.text = "EDIT MODE"
            status.backgroundTintList = ContextCompat.getColorStateList(activity, android.R.color.holo_orange_light)
            mainLabel.text = "Cancel Ed."
            btnLog.text = "Up. QSO"
            btnCancel.text = "Cancel Ed."

            // >>> ADIÇÃO: exibe/habilita o botão de deletar somente no Edit Mode
            btnDelete.visibility = View.VISIBLE
            btnDelete.isEnabled = true
        } else {
            status.text = "LOG MODE"
            status.backgroundTintList = ContextCompat.getColorStateList(activity, android.R.color.holo_green_light)
            mainLabel.text = "Main Menu"
            btnLog.text = "Log QSO"
            btnCancel.text = "Cancel Logger"

            //oculta/desabilita fora do Edit Mode
            btnDelete.visibility = View.GONE
            btnDelete.isEnabled = false
        }
    }


    // Cria/atualiza memória com base nos campos atuais do logger.
    fun createOrUpdateMemory(activity: MainActivity) {
        val rxCall = activity.findViewById<EditText>(R.id.editText_RX_Call).text.toString().trim().uppercase()
        if (rxCall.isEmpty()) return

        val qrgStr = activity.findViewById<TextView>(R.id.qrg_indicator).text.toString().trim()
        val freqKHz = qrgStringToKHz(qrgStr) ?: return

        val rxRst  = activity.findViewById<EditText>(R.id.editText_RX_RST).text.toString().trim().ifEmpty { null }
        val txRst  = activity.findViewById<EditText>(R.id.editText_TX_RST).text.toString().trim().ifEmpty { null }
        val rxNr   = activity.findViewById<EditText>(R.id.editText_RX_Nr).text.toString().trim().ifEmpty { null }
        val rxExch = activity.findViewById<EditText>(R.id.editText_RX_Exch).text.toString().trim().ifEmpty { null }
        val txExch = activity.findViewById<EditText>(R.id.editText_TX_Exch).text.toString().trim().ifEmpty { null }

        val mem = MemoryQSO(freqKHz, rxCall, rxRst, txRst, rxNr, rxExch, txExch)
        upsertMemoryAt(freqKHz, mem)

        activity.findViewById<TextView>(R.id.textView_log_memory).text = rxCall
    }

    // Aplica a memória próxima da QRG atual nos campos do logger.
    fun applyMemoryIfNear(activity: MainActivity) {
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
    }

    // Atualiza a sugestão visual da memória atual (somente pag_8).
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

    private fun bandOf(freqMHz: Double?): Band? {
        if (freqMHz == null || freqMHz.isNaN() || freqMHz <= 0.0) return null
        return bandTable.firstOrNull { freqMHz >= it.fLowMHz && freqMHz <= it.fHighMHz }
    }

    private fun normalizeModeForDupe(modeRaw: String?): String? {
        if (modeRaw.isNullOrBlank()) return null
        return when (modeRaw.trim().uppercase()) {
            "LSB", "USB" -> "SSB"
            "CWR", "CW-R" -> "CW"   // <- inclui CW-R
            else -> modeRaw.trim().uppercase()
        }
    }


    // "7.074.00" -> kHz -> MHz
    private fun qrgStringToMHzOrNull(qrgStr: String?): Double? {
        if (qrgStr.isNullOrBlank()) return null
        val kHz = qrgStringToKHz(qrgStr) ?: return null  // remove pontos e divide corretamente
        return kHz / 1000.0
    }

    fun checkDupeWithBtInterp(
        db: SQLiteDatabase,
        contestId: Long,
        callRx: String?,
        qrgInput: String?,
        modeRaw: String?
    ): DupeResult {
        val call = callRx?.trim()?.uppercase()
        if (call.isNullOrEmpty()) return DupeResult(false)

        val freqMHzAtual = qrgStringToMHzOrNull(qrgInput)
        val bandAtual = bandOf(freqMHzAtual) ?: return DupeResult(false)

        val modeNorm = normalizeModeForDupe(modeRaw) ?: return DupeResult(false)
        val modesForMatch: List<String> = when (modeNorm) {
            "SSB" -> listOf("SSB", "USB", "LSB")
            "CW" -> listOf("CW", "CWR")
            else -> listOf(modeNorm)
        }

        val placeholders = modesForMatch.joinToString(",") { "?" }
        val sql = """
            SELECT id, timestamp, freq, mode
            FROM QSOS
            WHERE contest_id = ?
              AND call = ?
              AND mode IN ($placeholders)
            ORDER BY datetime(timestamp) ASC
        """.trimIndent()

        val args = ArrayList<String>(2 + modesForMatch.size).apply {
            add(contestId.toString())
            add(call)
            addAll(modesForMatch)
        }.toTypedArray()

        db.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val timestamp = c.getString(1)
                val freqStr = c.getString(2)
                val modeStored = c.getString(3)

                val fMHzStored = qrgStringToMHzOrNull(freqStr)
                val bandStored = bandOf(fMHzStored)

                if (bandStored?.name == bandAtual.name) {
                    return DupeResult(
                        isDupe = true,
                        qsoId = id,
                        timestampUtc = timestamp,
                        freqMHz = fMHzStored,
                        modeStored = modeStored,
                        bandName = bandAtual.name
                    )
                }
            }
        }
        return DupeResult(false)
    }
    // --- DUPES: checagem em tempo real durante a digitação ---
    enum class DupeStatus {
        NOT_APPLICABLE,   // Ainda não é hora de checar (ex: <4 caracteres, QRG não encontrada, etc.)
        NOT_DUPE,         // Não é dupe
        DUPE              // É dupe!
    }

    // Checagem de DUPE em tempo real durante a digitação.
    // Regra: mesma QRG + mesmo MODO, e só alerta com CALL completo (>= 4 chars).
    // Checagem de DUPE em tempo real durante a digitação.
// Regra: MESMA BANDA + MESMO MODO (normalizado) + MESMO CALL (exato, a partir de 4 chars).
    fun checkDupeRealtime(
        activity: MainActivity,
        currentQrg: String?,
        currentMode: String?,
        partialCall: String?
    ): DupeResult {
        // CHAVE LÓGICA: em Edit Mode, verificador totalmente desligado
        if (LoggerManager.isEditing) return DupeResult(false)
        // Pré‑condições mínimas
        if (currentQrg.isNullOrBlank() || currentMode.isNullOrBlank()) return DupeResult(false)

        val callTyped = partialCall?.trim()?.uppercase() ?: return DupeResult(false)
        if (callTyped.length < 4) return DupeResult(false)

        // BANDA atual a partir da QRG
        val fMHz = qrgStringToMHzOrNull(currentQrg) ?: return DupeResult(false)
        val currentBand = bandOf(fMHz)?.name ?: return DupeResult(false)

        // Usuário/contest ativos (padrão do seu projeto)
        val userCall = activity.findViewById<TextView>(R.id.user_indicator).text.toString().trim()
        val contestName = activity.findViewById<TextView>(R.id.contest_indicator).text.toString().trim()
        if (userCall.isEmpty() || userCall == "USER?" || contestName.isEmpty() || contestName == "CONTEST?") {
            return DupeResult(false)
        }

        val dbFile = File(activity.filesDir, "db/$userCall.db")
        if (!dbFile.exists()) return DupeResult(false)

        try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                // Contest atual
                val contestId = db.rawQuery(
                    "SELECT id FROM Contest WHERE DisplayName = ? ORDER BY datetime(StartTime) DESC LIMIT 1",
                    arrayOf(contestName)
                ).use { c ->
                    if (!c.moveToFirst()) return DupeResult(false)
                    c.getLong(0)
                }

                // Normalização de modo (SSB=USB/LSB; CW=CWR)
                val modeNorm = normalizeModeForDupe(currentMode) ?: return DupeResult(false)
                val modesForMatch = when (modeNorm) {
                    "SSB" -> listOf("SSB", "USB", "LSB")
                    "CW"  -> listOf("CW", "CWR")
                    else  -> listOf(modeNorm)
                }

                // Buscar por MESMO CALL + modos equivalentes, independente da QRG.
                val placeholders = modesForMatch.joinToString(",") { "?" }
                val sql = """
                SELECT id, timestamp, freq, mode, call
                  FROM QSOS
                 WHERE contest_id = ?
                   AND UPPER(call) = ?
                   AND mode IN ($placeholders)
            """.trimIndent()
                val args = ArrayList<String>(2 + modesForMatch.size).apply {
                    add(contestId.toString())
                    add(callTyped)
                    addAll(modesForMatch)
                }.toTypedArray()

                db.rawQuery(sql, args).use { c ->
                    while (c.moveToNext()) {
                        val id            = c.getLong(0)
                        val ts            = c.getString(1)
                        val freqStoredStr = c.getString(2)
                        val modeStored    = c.getString(3)

                        // BANDA do QSO já logado
                        val fStoredMHz = qrgStringToMHzOrNull(freqStoredStr)
                        val storedBand = fStoredMHz?.let { bandOf(it)?.name }

                        // Se a banda do QSO salvo for a mesma da banda atual → DUPE
                        if (storedBand != null && storedBand == currentBand) {
                            return DupeResult(
                                isDupe       = true,
                                qsoId        = id,
                                timestampUtc = ts,
                                freqMHz      = fMHz,
                                modeStored   = modeStored,
                                bandName     = currentBand
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // segue padrão silencioso do projeto
        }

        return DupeResult(false)
    }



}