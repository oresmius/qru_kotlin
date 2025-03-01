package com.py6fx.qru

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import java.io.File
import kotlin.coroutines.coroutineContext

class ContestManager(private val context: Context) {

    fun createContestInstance(page: ConstraintLayout, dbPath: File) {
        try {
            val userDb = SQLiteDatabase.openOrCreateDatabase(dbPath, null)

            val createTableQuery = """
                CREATE TABLE IF NOT EXISTS Contest (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    StartTime DATETIME DEFAULT CURRENT_TIMESTAMP,
                    Name TEXT,
                    DisplayName TEXT,
                    CabrilloName TEXT,
                    Operator TEXT,
                    Band TEXT,
                    Power TEXT,
                    Mode TEXT,
                    Overlay TEXT,
                    Station TEXT,
                    Assisted TEXT,
                    Transmitter TEXT,
                    TimeCategory TEXT,
                    SendExchange TEXT,
                    Operators TEXT
                )
            """.trimIndent()
            userDb.execSQL(createTableQuery)

            val spinnerContests = page.findViewById<Spinner>(R.id.spinner_contests)
            val selectedContest = spinnerContests.selectedItem?.toString() ?: ""

            if (selectedContest.isEmpty()) {
                showToast("No contest selected!")
                return
            }

            val mainDbPath = File(context.filesDir, "db/main.qru")
            if (!mainDbPath.exists()) {
                showToast("Main contest database not found!")
                return
            }

            val mainDb = SQLiteDatabase.openDatabase(mainDbPath.path, null, SQLiteDatabase.OPEN_READONLY)
            val cursor = mainDb.rawQuery(
                "SELECT Name, DisplayName, CabrilloName FROM Contest WHERE DisplayName = ?",
                arrayOf(selectedContest)
            )

            if (!cursor.moveToFirst()) {
                showToast("Contest data not found!")
                cursor.close()
                mainDb.close()
                return
            }

            val name = cursor.getString(0)
            val displayName = cursor.getString(1)
            val cabrilloName = cursor.getString(2)

            cursor.close()
            mainDb.close()

            val spinners = page.children.filterIsInstance<Spinner>().toList()
            val invalidSelections = spinners
                .map { it.selectedItem?.toString() ?: "" }
                .filter { it.endsWith("?") || it.isEmpty() }

            if (invalidSelections.isNotEmpty()) {
                showToast("These options are invalid: ${invalidSelections.joinToString(", ")}")
                return
            }

            val editTexts = page.children.filterIsInstance<EditText>().toList()
            val emptyFields = editTexts.filter { it.text.toString().trim().isEmpty() }

            if (emptyFields.isNotEmpty()) {
                showToast("These fields cannot be null: ${emptyFields.joinToString(", ") { it.hint?.toString() ?: "Unnamed" }}")
                return
            }

            val insertQuery = """
                INSERT INTO Contest (Name, DisplayName, CabrilloName, Operator, Band, Power, Mode, Overlay, Station, Assisted, Transmitter, TimeCategory, SendExchange, Operators) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            userDb.execSQL(insertQuery, arrayOf(
                name, displayName, cabrilloName,
                page.findViewById<Spinner>(R.id.spinner_operator).selectedItem.toString(),
                page.findViewById<Spinner>(R.id.spinner_band).selectedItem.toString(),
                page.findViewById<Spinner>(R.id.spinner_power).selectedItem.toString(),
                page.findViewById<Spinner>(R.id.spinner_mode).selectedItem.toString(),
                page.findViewById<Spinner>(R.id.spinner_overlay).selectedItem.toString(),
                page.findViewById<Spinner>(R.id.spinner_station).selectedItem.toString(),
                page.findViewById<Spinner>(R.id.spinner_assisted).selectedItem.toString(),
                page.findViewById<Spinner>(R.id.spinner_transmitter).selectedItem.toString(),
                page.findViewById<Spinner>(R.id.spinner_time_category).selectedItem.toString(),
                page.findViewById<EditText>(R.id.editText_send_exchange).text.toString().trim(),
                page.findViewById<EditText>(R.id.editText_operators).text.toString().trim()
            ))

            userDb.close()

            showToast("Contest $displayName created successfully!")

        } catch (e: SQLiteException) {
            showToast("Error creating contest: ${e.message}")
        }
    }

    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    fun loadContests(newContestLoad: View) {
        val spinnerContests = newContestLoad.findViewById<Spinner>(R.id.spinner_contests)
        val dbPath = File(context.filesDir, "db/main.qru")

        if (!dbPath.exists()) {
            Toast.makeText(context, "Error: Database main.qru not found!", Toast.LENGTH_LONG).show()
            return
        }

        val db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READONLY)
        val cursor = db.rawQuery("SELECT DisplayName FROM CONTEST", null)
        val contests = mutableListOf<String>()

        if (cursor.moveToFirst()) {
            do {
                val displayName = cursor.getString(0)
                contests.add(displayName)
            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()
        contests.sort()

        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, contests)
        spinnerContests.adapter = adapter

        // Configuração dos spinners dentro da mesma função
        newContestLoad.findViewById<Spinner>(R.id.spinner_operator).adapter =
            ArrayAdapter.createFromResource(context, R.array.operator, android.R.layout.simple_spinner_dropdown_item)

        newContestLoad.findViewById<Spinner>(R.id.spinner_band).adapter =
            ArrayAdapter.createFromResource(context, R.array.band, android.R.layout.simple_spinner_dropdown_item)

        newContestLoad.findViewById<Spinner>(R.id.spinner_power).adapter =
            ArrayAdapter.createFromResource(context, R.array.power, android.R.layout.simple_spinner_dropdown_item)

        newContestLoad.findViewById<Spinner>(R.id.spinner_mode).adapter =
            ArrayAdapter.createFromResource(context, R.array.mode, android.R.layout.simple_spinner_dropdown_item)

        newContestLoad.findViewById<Spinner>(R.id.spinner_overlay).adapter =
            ArrayAdapter.createFromResource(context, R.array.overlay, android.R.layout.simple_spinner_dropdown_item)

        newContestLoad.findViewById<Spinner>(R.id.spinner_station).adapter =
            ArrayAdapter.createFromResource(context, R.array.station, android.R.layout.simple_spinner_dropdown_item)

        newContestLoad.findViewById<Spinner>(R.id.spinner_assisted).adapter =
            ArrayAdapter.createFromResource(context, R.array.asssisted, android.R.layout.simple_spinner_dropdown_item)

        newContestLoad.findViewById<Spinner>(R.id.spinner_transmitter).adapter =
            ArrayAdapter.createFromResource(context, R.array.transmitter, android.R.layout.simple_spinner_dropdown_item)

        newContestLoad.findViewById<Spinner>(R.id.spinner_time_category).adapter =
            ArrayAdapter.createFromResource(context, R.array.time_category, android.R.layout.simple_spinner_dropdown_item)
    }
}




