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

class ContestManager(private val context: Context, private val activity: MainActivity) {

    // Tornamos os Spinners acessíveis globalmente dentro da classe
    private lateinit var spinnerContests: Spinner
    private lateinit var spinnerOperator: Spinner
    private lateinit var spinnerBand: Spinner
    private lateinit var spinnerPower: Spinner
    private lateinit var spinnerMode: Spinner
    private lateinit var spinnerOverlay: Spinner
    private lateinit var spinnerStation: Spinner
    private lateinit var spinnerAssisted: Spinner
    private lateinit var spinnerTransmitter: Spinner
    private lateinit var spinnerTimeCategory: Spinner

    fun loadContests(newContestLoad: View) {
        // Inicializa os Spinners associando às Views
        spinnerContests = newContestLoad.findViewById(R.id.spinner_contests)
        spinnerOperator = newContestLoad.findViewById(R.id.spinner_operator)
        spinnerBand = newContestLoad.findViewById(R.id.spinner_band)
        spinnerPower = newContestLoad.findViewById(R.id.spinner_power)
        spinnerMode = newContestLoad.findViewById(R.id.spinner_mode)
        spinnerOverlay = newContestLoad.findViewById(R.id.spinner_overlay)
        spinnerStation = newContestLoad.findViewById(R.id.spinner_station)
        spinnerAssisted = newContestLoad.findViewById(R.id.spinner_assisted)
        spinnerTransmitter = newContestLoad.findViewById(R.id.spinner_transmitter)
        spinnerTimeCategory = newContestLoad.findViewById(R.id.spinner_time_category)

        // Caminho do banco de dados principal
        val dbPath = File(context.filesDir, "db/main.qru")

        // Verifica se o banco de dados existe
        if (!dbPath.exists()) {
            Toast.makeText(context, "Error: Database main.qru not found!", Toast.LENGTH_LONG).show()
            return
        }

        // Abre o banco de dados para leitura
        val db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READONLY)
        val cursor = db.rawQuery("SELECT DisplayName FROM Contest", null)

        // Lista para armazenar os contests recuperados
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

        // Define o adaptador para o spinner de contests
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, contests)
        spinnerContests.adapter = adapter

        // Configuração dos adaptadores para os Spinners
        spinnerOperator.adapter = ArrayAdapter.createFromResource(
            context,
            R.array.operator,
            android.R.layout.simple_spinner_dropdown_item
        )

        spinnerBand.adapter = ArrayAdapter.createFromResource(
            context,
            R.array.band,
            android.R.layout.simple_spinner_dropdown_item
        )

        spinnerPower.adapter = ArrayAdapter.createFromResource(
            context,
            R.array.power,
            android.R.layout.simple_spinner_dropdown_item
        )

        spinnerMode.adapter = ArrayAdapter.createFromResource(
            context,
            R.array.mode,
            android.R.layout.simple_spinner_dropdown_item
        )

        spinnerOverlay.adapter = ArrayAdapter.createFromResource(
            context,
            R.array.overlay,
            android.R.layout.simple_spinner_dropdown_item
        )

        spinnerStation.adapter = ArrayAdapter.createFromResource(
            context,
            R.array.station,
            android.R.layout.simple_spinner_dropdown_item
        )

        spinnerAssisted.adapter = ArrayAdapter.createFromResource(
            context,
            R.array.asssisted,
            android.R.layout.simple_spinner_dropdown_item
        )

        spinnerTransmitter.adapter = ArrayAdapter.createFromResource(
            context,
            R.array.transmitter,
            android.R.layout.simple_spinner_dropdown_item
        )

        spinnerTimeCategory.adapter = ArrayAdapter.createFromResource(
            context,
            R.array.time_category,
            android.R.layout.simple_spinner_dropdown_item
        )
    }



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
        val cursor = db.rawQuery(
            "SELECT StartTime, DisplayName FROM Contest ORDER BY datetime(StartTime) DESC",
            null
        )


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
        contests.sortDescending()

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
    fun editContest(page: View) {
        // Obtém referência ao spinner da pag_6
        val spinner = page.findViewById<Spinner>(R.id.spinner_contests_initialized)
        val selectedItem = spinner.selectedItem?.toString()

        // Verifica se algum contest foi selecionado
        if (selectedItem.isNullOrEmpty()) {
            showToast("No contest selected!")
            return
        }

        // Extrai apenas o DisplayName (ignora a data/hora de início)
        val contestDisplayName = selectedItem.substringAfter(" - ").trim()

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

        try {
            // Abre o banco de dados em modo leitura
            val db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READONLY)

            // Consulta a coluna 'Band' do contest usando DisplayName
            val cursor = db.rawQuery(
                "SELECT Band FROM Contest WHERE DisplayName = ?",
                arrayOf(contestDisplayName)
            )

            if (cursor.moveToFirst()) {
                val bandValue = cursor.getString(0) // Obtém o valor da coluna 'Band'

                // Fecha o cursor e o banco de dados
                cursor.close()
                db.close()

                // Obtém referência ao spinner_band na pag_5
                val spinnerBand = activity.findViewById<Spinner>(R.id.spinner_band)

                // Usa o adaptador existente (não recria)
                val adapter = spinnerBand.adapter
                if (adapter != null) {
                    for (i in 0 until adapter.count) {
                        if (adapter.getItem(i).toString() == bandValue) {
                            spinnerBand.setSelection(i)
                            break
                        }
                    }
                    showToast("Band loaded successfully!")
                } else {
                    showToast("Error: Adapter not found! loadContests was not called?")
                }

            } else {
                showToast("No data found for contest: $contestDisplayName")
                cursor.close()
                db.close()
            }

        } catch (e: SQLiteException) {
            showToast("Error loading contest: ${e.message}")
        }
    }

}



