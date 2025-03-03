package com.py6fx.qru

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import java.io.File

class ContestManager(private val context: Context, private val activity: MainActivity){
    fun createContestInstance(page: ConstraintLayout, dbPath: File) {
        try {
            val userDb = SQLiteDatabase.openOrCreateDatabase(dbPath.path, null)

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

            val mainDb =
                SQLiteDatabase.openDatabase(mainDbPath.path, null, SQLiteDatabase.OPEN_READONLY)
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

            userDb.execSQL(
                insertQuery, arrayOf(
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
                )
            )

            userDb.close()

            showToast("Contest $displayName created successfully!")

            //atualiza a exibição do conteste
            contestIndicator(displayName)

            activity.navigateToPage(3)

        }
        catch (e: SQLiteException) {
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
            ArrayAdapter.createFromResource(
                context,
                R.array.operator,
                android.R.layout.simple_spinner_dropdown_item
            )

        newContestLoad.findViewById<Spinner>(R.id.spinner_band).adapter =
            ArrayAdapter.createFromResource(
                context,
                R.array.band,
                android.R.layout.simple_spinner_dropdown_item
            )

        newContestLoad.findViewById<Spinner>(R.id.spinner_power).adapter =
            ArrayAdapter.createFromResource(
                context,
                R.array.power,
                android.R.layout.simple_spinner_dropdown_item
            )

        newContestLoad.findViewById<Spinner>(R.id.spinner_mode).adapter =
            ArrayAdapter.createFromResource(
                context,
                R.array.mode,
                android.R.layout.simple_spinner_dropdown_item
            )

        newContestLoad.findViewById<Spinner>(R.id.spinner_overlay).adapter =
            ArrayAdapter.createFromResource(
                context,
                R.array.overlay,
                android.R.layout.simple_spinner_dropdown_item
            )

        newContestLoad.findViewById<Spinner>(R.id.spinner_station).adapter =
            ArrayAdapter.createFromResource(
                context,
                R.array.station,
                android.R.layout.simple_spinner_dropdown_item
            )

        newContestLoad.findViewById<Spinner>(R.id.spinner_assisted).adapter =
            ArrayAdapter.createFromResource(
                context,
                R.array.asssisted,
                android.R.layout.simple_spinner_dropdown_item
            )

        newContestLoad.findViewById<Spinner>(R.id.spinner_transmitter).adapter =
            ArrayAdapter.createFromResource(
                context,
                R.array.transmitter,
                android.R.layout.simple_spinner_dropdown_item
            )

        newContestLoad.findViewById<Spinner>(R.id.spinner_time_category).adapter =
            ArrayAdapter.createFromResource(
                context,
                R.array.time_category,
                android.R.layout.simple_spinner_dropdown_item
            )
    }

    // Função para atualizar o indicador de contest
    fun contestIndicator(contest: String?) {
        val contestIndicator = activity.findViewById<TextView?>(R.id.contest_indicator)
        if (contestIndicator != null) {
            contestIndicator.text = contest ?: "No contest"
        }
    }

    //função que acrrega os contestes inicializados

    fun loadInitializedContests(initializedContests: View) {
        // Obtém referência ao spinner na pag_6
        val spinner = initializedContests.findViewById<Spinner>(R.id.spinner_contests_initialized)

        // Obtém o usuário ativo pelo indicador
        val userIndicator = activity.findViewById<TextView>(R.id.user_indicator)
        val activeUser = userIndicator.text.toString().trim()

        // Verifica se há um usuário ativo
        if (activeUser.isEmpty() || activeUser == "USER") {
            showToast("No active user selected!")
            return
        }

        // Caminho do banco de dados do usuário ativo
        val dbPath = File(context.filesDir, "db/$activeUser.db")

        // Verifica se o banco de dados do usuário existe
        if (!dbPath.exists()) {
            showToast("Error: Database for user $activeUser not found!")
            return
        }

        // Abre o banco de dados em modo leitura
        val db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READONLY)

        // Consulta os contests armazenados do usuário ativo
        val cursor = db.rawQuery("SELECT StartTime, DisplayName FROM Contest", null)
        val contests = mutableListOf<String>()

        // Processa os resultados
        if (cursor.moveToFirst()) {
            do {
                val startTime = cursor.getString(0)
                val displayName = cursor.getString(1)
                contests.add("$startTime - $displayName")
            } while (cursor.moveToNext())
        }

        // Fecha o cursor e o banco de dados
        cursor.close()
        db.close()

        // Ordena os contests para melhor exibição
        contests.sort()

        // Atualiza o spinner com os contests recuperados
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, contests)
        spinner.adapter = adapter
    }
    fun resumeContest(page: View) {
        // Obtém referência ao spinner na pag_6
        val spinner = page.findViewById<Spinner>(R.id.spinner_contests_initialized)
        val selectedItem = spinner.selectedItem?.toString()

        // Verifica se algum contest foi selecionado
        if (selectedItem.isNullOrEmpty()) {
            showToast("No contest selected!")
            return
        }

        // Extrai apenas o DisplayName (ignora a data/hora de início)
        val selectedContest = selectedItem.substringAfter(" - ").trim()

        // Atualiza o contest_indicator na UI
        contestIndicator(selectedContest)

        // Exibe mensagem de sucesso
        showToast("Contest $selectedContest resumed successfully!")

        // Navega automaticamente para o Main Menu (página 4)
        activity.navigateToPage(3)
    }

}