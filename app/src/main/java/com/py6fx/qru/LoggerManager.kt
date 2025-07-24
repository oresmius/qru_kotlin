package com.py6fx.qru

import android.database.sqlite.SQLiteDatabase
import android.widget.EditText

class LoggerManager {
    fun RSTAutomatico(modo: String, editTextTX: EditText, editTextRX: EditText) {
        val modosFonia = setOf("LSB", "USB", "FM", "AM")
        val rst = if (modosFonia.contains(modo.uppercase())) "59" else "599"
        editTextTX.setText(rst)
        editTextRX.setText(rst)
    }

    fun preencherTXExch(
        activity: MainActivity
    ) {
        // Obtém usuário ativo e contest ativo da interface
        val userCall = activity.findViewById<android.widget.TextView>(R.id.user_indicator).text.toString().trim()
        val contestName = activity.findViewById<android.widget.TextView>(R.id.contest_indicator).text.toString().trim()

        if (userCall.isEmpty() || userCall == "USER?") return
        if (contestName.isEmpty() || contestName == "CONTEST?") return

        // Caminho do banco do usuário ativo
        val dbPath = java.io.File(activity.filesDir, "db/$userCall.db")
        if (!dbPath.exists()) return

        try {
            val db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READONLY)
            val cursor = db.rawQuery(
                "SELECT SendExchange FROM Contest WHERE DisplayName = ? ORDER BY datetime(StartTime) DESC LIMIT 1",
                arrayOf(contestName)
            )
            if (cursor.moveToFirst()) {
                val sendExch = cursor.getString(0)
                // Preenche o campo TX Exch do logger
                activity.findViewById<EditText>(R.id.editText_TX_Exch).setText(sendExch)
            }
            cursor.close()
            db.close()
        } catch (e: Exception) {
            // Silencioso, para não travar a UI
        }
    }
}
