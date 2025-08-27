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

// >>> Callback opcional para avisar que um contest foi ativado/retomado
interface ContestCallbacks {
    fun onContestActivated(displayName: String)
}

class ContestManager(private val context: Context, private val activity: MainActivity, private val callbacks: ContestCallbacks? = null){
    var editingContestId: Int? = null
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
            val createQsosTableQuery = """
               CREATE TABLE IF NOT EXISTS QSOS (
                   id INTEGER PRIMARY KEY AUTOINCREMENT,
                   contest_id INTEGER,
                   timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
                   call TEXT,
                   freq REAL,
                   mode TEXT,
                   sent_rst TEXT,
                   rcvd_rst TEXT,
                   sent_serial INTEGER,
                   rcvd_serial INTEGER,
                   sent_exchange TEXT,
                   rcvd_exchange TEXT,
                   FOREIGN KEY (contest_id) REFERENCES Contest(id)
               )
            """.trimIndent()
            userDb.execSQL(createQsosTableQuery)

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
            if (editingContestId == null) {
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
                        page.findViewById<EditText>(R.id.editText_send_exchange).text.toString()
                            .trim().uppercase(),
                        page.findViewById<EditText>(R.id.editText_operators).text.toString().trim().uppercase()
                    )
                )
                showToast("Contest $displayName created successfully!")

            } else {
                val updateQuery = """
                    UPDATE Contest SET
                        Name = ?, DisplayName = ?, CabrilloName = ?, Operator = ?, Band = ?, Power = ?, Mode = ?, Overlay = ?, Station = ?, Assisted = ?, Transmitter = ?, TimeCategory = ?, SendExchange = ?, Operators = ?
                    WHERE id = ?
                """.trimIndent()

                userDb.execSQL(
                    updateQuery, arrayOf(
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
                        page.findViewById<EditText>(R.id.editText_operators).text.toString().trim(),
                        editingContestId!!
                    )
                )
                showToast("Contest $displayName updated successfully!")
                editingContestId = null
            }

            userDb.close()

            //atualiza a exibição do conteste
            contestIndicator(displayName)
            // Notifica a ativação do contest para inicializar memórias históricas
            callbacks?.onContestActivated(displayName)

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
    fun resumeContest(resumeContest: View) {
        // Obtém referência ao spinner na pag_6
        val spinner = resumeContest.findViewById<Spinner>(R.id.spinner_contests_initialized)
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
        // Notifica a retomada do contest para inicializar memórias históricas
        callbacks?.onContestActivated(selectedContest)


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

            // Consulta os dados do contest usando apenas o DisplayName extraído
            val cursor = db.rawQuery(
                "SELECT id, Operator, Band, Power, Mode, Overlay, Station, Assisted, Transmitter, TimeCategory, SendExchange, Operators " +
                        "FROM Contest WHERE DisplayName = ?",
                arrayOf(contestDisplayName)
            )

            if (cursor.moveToFirst()) {
                editingContestId = cursor.getInt(0)
                val operatorValue = cursor.getString(1)
                val bandValue = cursor.getString(2)
                val powerValue = cursor.getString(3)
                val modeValue = cursor.getString(4)
                val overlayValue = cursor.getString(5)
                val stationValue = cursor.getString(6)
                val assistedValue = cursor.getString(7)
                val transmitterValue = cursor.getString(8)
                val timeCategoryValue = cursor.getString(9)
                val sendExchangeValue = cursor.getString(10)
                val operatorsValue = cursor.getString(11)

                cursor.close()
                db.close()

                // Função para atualizar um Spinner reutilizando a lógica de Band
                fun updateSpinner(spinnerId: Int, value: String, arrayRes: Int) {
                    val spinner = activity.findViewById<Spinner>(spinnerId)

                    if (spinner.adapter == null) {
                        val defaultAdapter = ArrayAdapter(
                            activity,
                            android.R.layout.simple_spinner_dropdown_item,
                            activity.resources.getStringArray(arrayRes)
                        )
                        spinner.adapter = defaultAdapter
                    }

                    val adapter = spinner.adapter
                    for (i in 0 until adapter.count) {
                        if (adapter.getItem(i).toString() == value) {
                            spinner.setSelection(i)
                            break
                        }
                    }
                }

                // Atualiza os Spinners corretamente
                updateSpinner(R.id.spinner_operator, operatorValue, R.array.operator)
                updateSpinner(R.id.spinner_band, bandValue, R.array.band)
                updateSpinner(R.id.spinner_power, powerValue, R.array.power)
                updateSpinner(R.id.spinner_mode, modeValue, R.array.mode)
                updateSpinner(R.id.spinner_overlay, overlayValue, R.array.overlay)
                updateSpinner(R.id.spinner_station, stationValue, R.array.station)
                updateSpinner(R.id.spinner_assisted, assistedValue, R.array.asssisted)
                updateSpinner(R.id.spinner_transmitter, transmitterValue, R.array.transmitter)
                updateSpinner(R.id.spinner_time_category, timeCategoryValue, R.array.time_category)

                // Atualiza os EditTexts
                activity.findViewById<EditText>(R.id.editText_send_exchange).setText(sendExchangeValue)
                activity.findViewById<EditText>(R.id.editText_operators).setText(operatorsValue)

                // Atualiza o próprio spinner_contests com o DisplayName correto
                val spinnerContests = activity.findViewById<Spinner>(R.id.spinner_contests)

                // Verifica se o Adapter está inicializado e o atribui se necessário
                if (spinnerContests.adapter == null) {

                    // Criamos um novo adapter baseado na lista de contests disponíveis
                    val defaultAdapter = ArrayAdapter(
                        activity,
                        android.R.layout.simple_spinner_dropdown_item,
                        arrayOf(contestDisplayName) // Adiciona o contest atual pelo menos como placeholder
                    )
                    spinnerContests.adapter = defaultAdapter
                }

                // Obtém o Adapter atualizado
                val adapter = spinnerContests.adapter

                // Percorre os itens do Spinner para encontrar o índice correto
                for (i in 0 until adapter.count) {
                    if (adapter.getItem(i).toString() == contestDisplayName) {
                        spinnerContests.setSelection(i)
                        break
                    }
                }
                for (i in 0 until adapter.count) {
                    println("Item $i: ${adapter.getItem(i)}")
                }


                showToast("Contest data loaded successfully!")

            } else {
                showToast("No data found for contest: $contestDisplayName")
                cursor.close()
                db.close()
            }

        } catch (e: SQLiteException) {
            showToast("Error loading contest: ${e.message}")
        }
        // Atualiza o texto do label para "Edit Contest"
        activity.findViewById<TextView>(R.id.label_new_contests).text = "Edit Contest"
        activity.navigateToPage(4)
    }
    fun resetContestForm() {
        // Reseta o label
        activity.findViewById<TextView>(R.id.label_new_contests).text = "New Contest"

        // Reseta os campos de texto
        activity.findViewById<EditText>(R.id.editText_send_exchange).setText("")
        activity.findViewById<EditText>(R.id.editText_operators).setText("")

        val spinnerContests = activity.findViewById<Spinner>(R.id.spinner_contests)

        // Força a atualização do Adapter do Spinner
        loadContests(spinnerContests.rootView)

        // Aguarda o recarregamento e reseta a seleção
        spinnerContests.post {
            spinnerContests.setSelection(-1, true)
        }

    }

}