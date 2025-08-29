package com.py6fx.qru

import android.database.sqlite.SQLiteDatabase
import android.widget.EditText
import android.widget.TextView
import java.io.File
import android.widget.Toast
import androidx.core.content.ContextCompat
import android.view.View
import android.widget.Button
import android.graphics.Color


class LoggerManager {
    // --- Pré-log: memórias voláteis (sessão atual) ---
    // --- Pré-log: memórias MANUAIS (sessão atual) ---
    private data class MemoryQSO(
        val freqKHz: Double,
        val rxCall: String,
        val rxRst: String?,
        val txRst: String?,
        val rxNr: String?,
        val rxExch: String?,
        val txExch: String?,
        // NOVOS METADADOS
        val modeNorm: String,   // modo normalizado (SSB≡LSB/USB; CW≡CWR)
        val bandName: String,   // nome da banda (ex.: "40m")
        val dupeAttached: Boolean  // true = criada com DUPE aceso → cor de HISTÓRICO
    )

    // --- Memória HISTÓRICA (derivada do banco do contest ativo) ---
    private data class HistMemQSO(
        val id: Long,
        val freqKHz: Double,
        val rxCall: String,
        val modeNorm: String,
        val timestampUtc: String
    )
    private enum class MemUiState { EMPTY, HISTORICAL, MANUAL }

    private fun setLogMemoryUi(activity: MainActivity, state: MemUiState, text: String?) {
        val tv = activity.findViewById<TextView>(R.id.textView_log_memory)
        tv.text = text ?: ""

        // Cores (ajustáveis depois, se preferir outros tons)
        val colorInt = when (state) {
            MemUiState.EMPTY -> android.graphics.Color.WHITE          // branco
            MemUiState.HISTORICAL -> android.graphics.Color.parseColor("#FAF014")
            MemUiState.MANUAL -> android.graphics.Color.parseColor("#39E639")
        }
        tv.setBackgroundColor(colorInt)
    }

    private val historical = mutableListOf<HistMemQSO>()


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
                    val dbRw = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READWRITE)

                    // Garante coluna e inicialização
                    ensureLastSerialColumnAndInit(dbRw, contestId.toLong())

                    // Lê o próximo serial a emitir (monotônico)
                    val sCur = dbRw.rawQuery(
                        "SELECT LastSerialIssued FROM Contest WHERE id = ?",
                        arrayOf(contestId.toString())
                    )
                    val nextSerial = if (sCur.moveToFirst()) sCur.getInt(0) else 1
                    sCur.close()
                    dbRw.close()

                    editTextTxNr.setText(nextSerial.toString())
                    editTextTxNr.isEnabled = false
                    editTextTxExch.setText("")
                    editTextTxExch.isEnabled = false
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
            var serialToUse: Int? = null
            var exchToUse: String? = null

            val cc = db.rawQuery(
                "SELECT SendExchange FROM Contest WHERE id = ?",
                arrayOf(contestId.toString())
            )
            val sendExchStr = if (cc.moveToFirst()) cc.getString(0)?.trim().orEmpty() else ""
            cc.close()

            if (sendExchStr == "#") {
                // Serial controlado pelo contador monotônico
                ensureLastSerialColumnAndInit(db, contestId.toLong())
                db.rawQuery(
                    "SELECT LastSerialIssued FROM Contest WHERE id = ?",
                    arrayOf(contestId.toString())
                ).use { sCur ->
                    serialToUse = if (sCur.moveToFirst()) sCur.getInt(0) else 1
                }
                exchToUse = null
            } else {
                // Contest de exchange fixo: sem serial, usa EXCH
                serialToUse = null
                exchToUse = sendExchStr
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
                    // >>> usar o serial monotônico calculado:
                    serialToUse,
                    rxNr.toIntOrNull(),
                    // >>> usar o exchange definido pela regra do contest:
                    exchToUse,
                    rxExch.ifEmpty { null }
                )
            )
            // >>> se for contest com serial, avançar o contador monotônico
            if (sendExchStr == "#") {
                db.execSQL(
                    "UPDATE Contest SET LastSerialIssued = LastSerialIssued + 1 WHERE id = ?",
                    arrayOf(contestId)
                )
            }

            // Mantém o toast de sucesso
            Toast.makeText(activity, "QSO logged successfully!", Toast.LENGTH_LONG).show()

            // Após o INSERT, pega o id inserido e atualiza cache histórico
            try {
                val cur = db.rawQuery("SELECT last_insert_rowid()", null)
                if (cur.moveToFirst()) {
                    val newId = cur.getLong(0)
                    onQsoInserted(activity, newId)
                }
                cur.close()
            } catch (_: Exception) { }

// Atualiza a sugestão visual
            updateMemorySuggestionForCurrentQrg(activity)

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
        // Sinaliza modo edição ANTES de tocar nos campos (evita TextWatcher acionar DUPE)
        isEditing = true
        editingQsoId = qsoId

        // (opcional) esconder banner logo de cara
        activity.hideDupeBanner()

        // Agora preenche os campos
        activity.findViewById<EditText>(R.id.editText_RX_Call).setText(rxCall)
        activity.findViewById<EditText>(R.id.editText_RX_RST).setText(rxRst)
        activity.findViewById<EditText>(R.id.editText_RX_Nr).setText(rxNr ?: "")
        activity.findViewById<EditText>(R.id.editText_RX_Exch).setText(rxExch ?: "")
        activity.findViewById<EditText>(R.id.editText_TX_RST).setText(txRst)
        activity.findViewById<EditText>(R.id.editText_TX_Exch).isEnabled = false
        activity.findViewById<EditText>(R.id.editText_TX_Nr).isEnabled = false

        setEditModeUI(activity, true)

        // Garante que qualquer banner exibido por um disparo residual seja ocultado
        activity.hideDupeBanner()

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
            onQsoUpdated(activity, qsoId)
            updateMemorySuggestionForCurrentQrg(activity)
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

            // 1) Coleta dados do QSO "mãe" antes de apagar
            var callUpper: String? = null
            var bandName: String? = null
            var modeNorm: String? = null
            db.rawQuery("SELECT call, freq, mode FROM QSOS WHERE id = ? LIMIT 1", arrayOf(qsoId.toString())).use { c ->
                if (c.moveToFirst()) {
                    callUpper = (c.getString(0) ?: "").trim().uppercase()
                    val freqStr = c.getString(1)
                    val rawMode = c.getString(2)
                    bandName = bandNameForQrgStr(freqStr)
                    modeNorm = normalizeModeForMemory(rawMode)
                }
            }

            // 2) Apaga no banco
            db.execSQL("DELETE FROM QSOS WHERE id = ?", arrayOf(qsoId))
            db.close()

            // 3) Remove do cache HISTÓRICO e PURGA memórias MANUAIS relacionadas
            onQsoDeleted(qsoId)
            if (!callUpper.isNullOrEmpty()) purgeManualMemories(callUpper!!, bandName, modeNorm)

            // 4) Sai do Edit Mode, limpa campos e atualiza sugestão
            sairDoEditMode(activity)
            limparCamposQSO(activity)
            updateMemorySuggestionForCurrentQrg(activity)

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

        val qrgStr  = activity.findViewById<TextView>(R.id.qrg_indicator).text.toString().trim()
        val modoStr = activity.findViewById<TextView>(R.id.mode_indicator).text.toString().trim()
        val freqKHz = qrgStringToKHz(qrgStr) ?: return

        val rxRst  = activity.findViewById<EditText>(R.id.editText_RX_RST).text.toString().trim().ifEmpty { null }
        val txRst  = activity.findViewById<EditText>(R.id.editText_TX_RST).text.toString().trim().ifEmpty { null }
        val rxNr   = activity.findViewById<EditText>(R.id.editText_RX_Nr).text.toString().trim().ifEmpty { null }
        val rxExch = activity.findViewById<EditText>(R.id.editText_RX_Exch).text.toString().trim().ifEmpty { null }
        val txExch = activity.findViewById<EditText>(R.id.editText_TX_Exch).text.toString().trim().ifEmpty { null }

        // Metadados necessários
        val modeNorm = normalizeModeForMemory(modoStr) ?: return
        val bandName = bandNameForQrgStr(qrgStr) ?: return

        // Determina se é DUPE agora (equivalente a “banner aceso”)
        var dupeNow = false
        try {
            val userCall = activity.findViewById<TextView>(R.id.user_indicator).text.toString().trim()
            val contestName = activity.findViewById<TextView>(R.id.contest_indicator).text.toString().trim()
            val dbFile = File(activity.filesDir, "db/$userCall.db")
            if (userCall.isNotEmpty() && contestName.isNotEmpty() && dbFile.exists()) {
                SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    val contestId = db.rawQuery(
                        "SELECT id FROM Contest WHERE DisplayName = ? ORDER BY datetime(StartTime) DESC LIMIT 1",
                        arrayOf(contestName)
                    ).use { c ->
                        if (!c.moveToFirst()) null else c.getLong(0)
                    }
                    if (contestId != null) {
                        val res = checkDupeWithBtInterp(
                            db        = db,
                            contestId = contestId,
                            callRx    = rxCall,
                            qrgInput  = qrgStr,
                            modeRaw   = modoStr
                        )
                        dupeNow = res.isDupe
                    }
                }
            }
        } catch (_: Exception) {}

        val mem = MemoryQSO(
            freqKHz = freqKHz,
            rxCall  = rxCall,
            rxRst   = rxRst,
            txRst   = txRst,
            rxNr    = rxNr,
            rxExch  = rxExch,
            txExch  = txExch,
            modeNorm = modeNorm,
            bandName = bandName,
            dupeAttached = dupeNow
        )
        upsertMemoryAt(freqKHz, mem)

        // Pintura conforme regra: dupe-anexada = amarelo (histórica); senão, verde (manual)
        if (dupeNow) {
            setLogMemoryUi(activity, MemUiState.HISTORICAL, rxCall)
        } else {
            setLogMemoryUi(activity, MemUiState.MANUAL, rxCall)
        }
    }


    // Aplica a memória próxima da QRG atual nos campos do logger.
    fun applyMemoryIfNear(activity: MainActivity) {
        if (activity.viewFlipper.displayedChild != 7) return

        val qrgStr  = activity.findViewById<TextView>(R.id.qrg_indicator).text.toString().trim()
        val modeStr = activity.findViewById<TextView>(R.id.mode_indicator).text.toString().trim()
        val freqKHz = qrgStringToKHz(qrgStr) ?: return
        val modeNorm = normalizeModeForMemory(modeStr) ?: return

        val etCall  = activity.findViewById<EditText>(R.id.editText_RX_Call)
        val etRxRst = activity.findViewById<EditText>(R.id.editText_RX_RST)
        val etTxRst = activity.findViewById<EditText>(R.id.editText_TX_RST)
        val etRxNr  = activity.findViewById<EditText>(R.id.editText_RX_Nr)
        val etRxEx  = activity.findViewById<EditText>(R.id.editText_RX_Exch)
        val etTxEx  = activity.findViewById<EditText>(R.id.editText_TX_Exch)

        // 1) MANUAL (dupe-anexada ou normal) — aplica TODOS os campos
        val memManual = findManualNearByMode(freqKHz, modeNorm)
        if (memManual != null) {
            etCall.setText(memManual.rxCall)
            etRxRst.setText(memManual.rxRst ?: "")
            etTxRst.setText(memManual.txRst ?: "")
            etRxNr.setText(memManual.rxNr ?: "")
            etRxEx.setText(memManual.rxExch ?: "")
            etTxEx.setText(memManual.txExch ?: "")
            return
        }

        // 2) HISTÓRICO — aplica apenas o CALL
        val memHist = findHistoricalNearByMode(freqKHz, modeNorm) ?: return
        etCall.setText(memHist.rxCall)
    }



    // Atualiza a sugestão visual da memória atual (somente pag_8).
    fun updateMemorySuggestionForCurrentQrg(activity: MainActivity) {
        if (activity.viewFlipper.displayedChild != 7) {
            setLogMemoryUi(activity, MemUiState.EMPTY, null); return
        }

        val qrgStr  = activity.findViewById<TextView>(R.id.qrg_indicator).text.toString().trim()
        val modeStr = activity.findViewById<TextView>(R.id.mode_indicator).text.toString().trim()
        val freqKHz = qrgStringToKHz(qrgStr) ?: run { setLogMemoryUi(activity, MemUiState.EMPTY, null); return }
        val modeNorm = normalizeModeForMemory(modeStr) ?: run { setLogMemoryUi(activity, MemUiState.EMPTY, null); return }

        // 1) Manual DUPE-ANEXADA
        findManualNearByMode(freqKHz, modeNorm)?.let { mem ->
            if (mem.dupeAttached) {
                setLogMemoryUi(activity, MemUiState.HISTORICAL, mem.rxCall)
                return
            }
        }

        // 2) Manual NORMAL
        findManualNearByMode(freqKHz, modeNorm)?.let { mem ->
            setLogMemoryUi(activity, MemUiState.MANUAL, mem.rxCall)
            return
        }

        // 3) HISTÓRICO (DB)
        val memHist = findHistoricalNearByMode(freqKHz, modeNorm)
        if (memHist != null) {
            setLogMemoryUi(activity, MemUiState.HISTORICAL, memHist.rxCall)
        } else {
            setLogMemoryUi(activity, MemUiState.EMPTY, null)
        }
    }


    // Zera todas as memórias voláteis e limpa a sugestão visual.
    fun clearAllMemories(activity: MainActivity) {
        memories.clear()
        // se sua versão tem 'historical', limpe também:
        try { historical.clear() } catch (_: Exception) {}
        setLogMemoryUi(activity, MemUiState.EMPTY, null)
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

    // Normalização reaproveita a do DUPE
    private fun normalizeModeForMemory(modeRaw: String?): String? = normalizeModeForDupe(modeRaw)

    private fun purgeManualMemories(callUpper: String, bandName: String?, modeNorm: String?) {
        if (bandName.isNullOrBlank() || modeNorm.isNullOrBlank()) return
        memories.removeAll { it.rxCall == callUpper && it.bandName == bandName && it.modeNorm == modeNorm }
    }


    // Banda a partir de uma QRG string; retorna "40m", "20m", etc.
    private fun bandNameForQrgStr(qrgStr: String?): String? {
        val fMHz = qrgStringToMHzOrNull(qrgStr) ?: return null
        return bandOf(fMHz)?.name
    }

    // Busca memória MANUAL mais próxima (± MEM_TOL_KHZ) E MESMO modo normalizado
    private fun findManualNearByMode(freqKHz: Double, modeNorm: String?): MemoryQSO? {
        if (modeNorm.isNullOrBlank()) return null
        val matches = memories.filter {
            kotlin.math.abs(it.freqKHz - freqKHz) <= MEM_TOL_KHZ && it.modeNorm == modeNorm
        }
        // em empate: a mais recente é a que foi inserida por último (fica “mais ao fim” da lista)
        return matches.lastOrNull()
    }


    // Encontra item histórico mais próximo (± MEM_TOL_KHZ) e MESMO modo (normalizado).
    private fun findHistoricalNearByMode(freqKHz: Double, modeNorm: String?): HistMemQSO? {
        if (modeNorm.isNullOrBlank()) return null
        val matches = historical.filter {
            kotlin.math.abs(it.freqKHz - freqKHz) <= MEM_TOL_KHZ && it.modeNorm == modeNorm
        }
        // timestampUtc está em formato ISO do SQLite → ordena lexicograficamente
        return matches.maxByOrNull { it.timestampUtc }
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
    // Inicializa a memória histórica lendo TODO o contest ativo
    fun initHistoricalFromDb(activity: MainActivity): Int {
        historical.clear()

        val userCall = activity.findViewById<TextView>(R.id.user_indicator).text.toString().trim()
        val contestName = activity.findViewById<TextView>(R.id.contest_indicator).text.toString().trim()
        val dbFile = File(activity.filesDir, "db/$userCall.db")
        if (userCall.isEmpty() || contestName.isEmpty() || !dbFile.exists()) return 0

        return try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                // Descobre contest_id
                val contestId = db.rawQuery(
                    "SELECT id FROM Contest WHERE DisplayName = ? ORDER BY datetime(StartTime) DESC LIMIT 1",
                    arrayOf(contestName)
                ).use { c ->
                    if (!c.moveToFirst()) return 0
                    c.getLong(0)
                }

                db.rawQuery(
                    "SELECT id, timestamp, freq, mode, call FROM QSOS WHERE contest_id = ?",
                    arrayOf(contestId.toString())
                ).use { q ->
                    while (q.moveToNext()) {
                        val id  = q.getLong(0)
                        val ts  = q.getString(1) ?: continue
                        val fqS = q.getString(2) ?: continue
                        val md  = q.getString(3) ?: continue
                        val cl  = q.getString(4) ?: continue

                        val kHz = qrgStringToKHz(fqS) ?: continue
                        val mNorm = normalizeModeForMemory(md) ?: continue

                        historical.add(HistMemQSO(id, kHz, cl.uppercase(), mNorm, ts))
                    }
                }
            }
            historical.size
        } catch (_: Exception) {
            0
        }
    }

    // Atualiza o cache histórico após INSERT (lê o próprio registro do DB)
    private fun onQsoInserted(activity: MainActivity, newRowId: Long) {
        val userCall = activity.findViewById<TextView>(R.id.user_indicator).text.toString().trim()
        val dbFile = File(activity.filesDir, "db/$userCall.db")
        if (!dbFile.exists()) return

        try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery(
                    "SELECT id, timestamp, freq, mode, call FROM QSOS WHERE id = ? LIMIT 1",
                    arrayOf(newRowId.toString())
                ).use { q ->
                    if (q.moveToFirst()) {
                        val id  = q.getLong(0)
                        val ts  = q.getString(1) ?: return
                        val fqS = q.getString(2) ?: return
                        val md  = q.getString(3) ?: return
                        val cl  = q.getString(4) ?: return

                        val kHz   = qrgStringToKHz(fqS) ?: return
                        val mNorm = normalizeModeForMemory(md) ?: return

                        // Remove duplicata de id (precaução) e insere atualizado
                        historical.removeAll { it.id == id }
                        historical.add(HistMemQSO(id, kHz, cl.uppercase(), mNorm, ts))
                    }
                }
            }
        } catch (_: Exception) { /* silencioso como no resto do projeto */ }
    }

    // Atualiza o cache histórico após UPDATE
    private fun onQsoUpdated(activity: MainActivity, qsoId: Int) {
        val userCall = activity.findViewById<TextView>(R.id.user_indicator).text.toString().trim()
        val dbFile = File(activity.filesDir, "db/$userCall.db")
        if (!dbFile.exists()) return

        try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery(
                    "SELECT id, timestamp, freq, mode, call FROM QSOS WHERE id = ? LIMIT 1",
                    arrayOf(qsoId.toString())
                ).use { q ->
                    if (q.moveToFirst()) {
                        val id  = q.getLong(0)
                        val ts  = q.getString(1) ?: return
                        val fqS = q.getString(2) ?: return
                        val md  = q.getString(3) ?: return
                        val cl  = q.getString(4) ?: return

                        val kHz   = qrgStringToKHz(fqS) ?: return
                        val mNorm = normalizeModeForMemory(md) ?: return

                        historical.removeAll { it.id == id }
                        historical.add(HistMemQSO(id, kHz, cl.uppercase(), mNorm, ts))
                    } else {
                        // Se não encontrar o registro (caso raro), remove do cache
                        historical.removeAll { it.id == qsoId.toLong() }
                    }
                }
            }
        } catch (_: Exception) { }
    }

    // Atualiza o cache histórico após DELETE
    private fun onQsoDeleted(qsoId: Int) {
        historical.removeAll { it.id == qsoId.toLong() }
    }


    private fun ensureLastSerialColumnAndInit(db: SQLiteDatabase, contestId: Long) {
        // 1) Se a coluna não existir, cria
        db.rawQuery("PRAGMA table_info(Contest)", null).use { c ->
            var hasCol = false
            while (c.moveToNext()) {
                if (c.getString(1).equals("LastSerialIssued", ignoreCase = true)) {
                    hasCol = true; break
                }
            }
            if (!hasCol) {
                db.execSQL("ALTER TABLE Contest ADD COLUMN LastSerialIssued INTEGER")
            }
        }

        // 2) Se o valor estiver nulo, inicializa com MAX(sent_serial)+1 (ou 1 se não houver QSOs)
        val cur = db.rawQuery(
            "SELECT LastSerialIssued FROM Contest WHERE id = ?",
            arrayOf(contestId.toString())
        )
        val needsInit = if (cur.moveToFirst()) cur.isNull(0) else true
        cur.close()

        if (needsInit) {
            val maxCur = db.rawQuery(
                "SELECT COALESCE(MAX(sent_serial), 0) + 1 FROM QSOS WHERE contest_id = ?",
                arrayOf(contestId.toString())
            )
            val startVal = if (maxCur.moveToFirst()) maxCur.getInt(0) else 1
            maxCur.close()
            db.execSQL(
                "UPDATE Contest SET LastSerialIssued = ? WHERE id = ?",
                arrayOf(startVal, contestId)
            )
        }
    }
}