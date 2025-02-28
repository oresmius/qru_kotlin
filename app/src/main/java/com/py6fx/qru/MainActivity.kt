package com.py6fx.qru

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children

class MainActivity : AppCompatActivity() {

    // Declaração universal de variável para o ViewFlipper
    private lateinit var viewFlipper: ViewFlipper

    //declaração universal do banco de dados em uso
    private lateinit var dbPath: File


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Verifica e cria a pasta "db" se não existir
        val dbFolder = File(applicationContext.filesDir, "db")
        if (!dbFolder.exists()) {
            dbFolder.mkdirs()
        }

        // Inicializa componentes
        initializeComponents()

        // Configura os botões
        setupButtonListeners()

        // Inicializa menus popup
        setupPopupMenu()

        // referência ao botão de salvar
        val buttonSaveUser = findViewById<Button>(R.id.button_save_user)
        buttonSaveUser.setOnClickListener {
            //chama o método que lida com o SQlite
            saveUserToDb()
        }
        // referência ao botão load user
        val buttonLoadUser = findViewById<Button>(R.id.button_load_user)
        buttonLoadUser.setOnClickListener{
            // chama o método que lida com os dbs que são os usuários
            loadUsers()
            navigateToPage(2)
        }

        // referência ao botão select
        val buttonSelectUser = findViewById<Button>(R.id.button_select_user)
        buttonSelectUser.setOnClickListener{
            // chama o método que seleciona o usuário
            selectUser()
        }
    }
        // método que salva os dados do usuário no bd
    private fun saveUserToDb() {
        // referências aos campos de editText
        val editTextCall = findViewById<EditText>(R.id.editText_new_user_call)
        val editTextName = findViewById<EditText>(R.id.editText_new_user_name)
        val editTextAddress = findViewById<EditText>(R.id.editText_new_user_address)
        val editTextCity = findViewById<EditText>(R.id.editText_new_user_city)
        val editTextState = findViewById<EditText>(R.id.editText_new_user_state)
        val editTextZIP = findViewById<EditText>(R.id.editText_new_user_zip)
        val editTextCountry = findViewById<EditText>(R.id.editText_new_user_country)
        val editTextGrid = findViewById<EditText>(R.id.editText_new_user_grid_square)
        val editTextARRL = findViewById<EditText>(R.id.editText_new_user_arrl_section)
        val editTextClub = findViewById<EditText>(R.id.editText_new_user_club)
        val editTextCQ = findViewById<EditText>(R.id.editText_new_user_cq_zone)
        val editTextITU = findViewById<EditText>(R.id.editText_new_user_itu_zone)
        val editTextEmail = findViewById<EditText>(R.id.editText_new_user_email)


            //coleta e labidação dos dados
            val call = editTextCall.text.toString().trim().uppercase()
            val name = editTextName.text.toString().trim()
            val address = editTextAddress.text.toString().trim()
            val city = editTextCity.text.toString().trim()
            val state = editTextState.text.toString().trim()
            val zip = editTextZIP.text.toString().trim()
            val country = editTextCountry.text.toString().trim()
            val grid = editTextGrid.text.toString().trim()
            val cq = editTextCQ.text.toString().trim()
            val itu = editTextITU.text.toString().trim()
            val arrl = editTextARRL.text.toString().trim()
            val club = editTextClub.text.toString().trim()
            val email = editTextEmail.text.toString().trim()

            // Lista de campos com seus nomes
            val fields = listOf(
                "Call" to call,
                "Name" to name,
                "Address" to address,
                "City" to city,
                "State" to state,
                "ZIP" to zip,
                "Country" to country,
                "Grid Square" to grid,
                "CQ Zone" to cq,
                "ITU Zone" to itu,
                "ARRL Section" to arrl,
                "Club" to club,
                "Email" to email
            )

            // Filtra os campos vazios e extrai seus nomes
            val missingFields = fields.filter { it.second.isEmpty() }.map { it.first }

            // Se houver campos vazios, exibe um alerta
            if (missingFields.isNotEmpty()) {
                val message = "Os seguintes campos estão vazios: ${missingFields.joinToString(", ")}"
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                return
            }
            // caminho para salvar o bd na pasta "db"
            val dbPath = File(applicationContext.filesDir, "db/$call.db")

            // Verifica se o banco já existe
            if (dbPath.exists()) {
                Toast.makeText(this, "A database with this name already exists. Please choose a different call sign.", Toast.LENGTH_LONG).show()
                return
            }
            try{
                //cria ou abre o bd
                val db = SQLiteDatabase.openOrCreateDatabase(dbPath, null)
                // cria a tabela "user" se ainda não existir
                val createTableQuery = """
                    CREATE TABLE IF NOT EXISTS user (
                        Call TEXT PRIMARY KEY,
                        Name TEXT,
                        Address TEXT,
                        City TEXT,
                        State TEXT,
                        ZIP TEXT,
                        Country TEXT,
                        GridSquare TEXT,
                        CQZone TEXT,
                        ITUZone TEXT,
                        ARRLSection TEXT,
                        Club TEXT,
                        Email TEXT
                )
            """.trimIndent()
                db.execSQL(createTableQuery)
                // Insere os dados na tabela
                val insertQuery = """
                INSERT INTO user (Call, Name, Address, City, State, ZIP, Country, GridSquare, CQZone, ITUZone, ARRLSection, Club, Email) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
                db.execSQL(insertQuery, arrayOf(call, name, address, city, state, zip, country, grid, cq, itu, arrl, club, email))

                // Fecha o banco de dados
                db.close()

                // Mensagem de sucesso
                Toast.makeText(this, "User successfully saved!", Toast.LENGTH_LONG).show()

            } catch (e: SQLiteException) {
                Toast.makeText(this, "Error saving user: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

    // método que lê os dbs que são os usuários e cria uma lista de seleção.
    private fun loadUsers() {
        val spinnerUsers = findViewById<Spinner>(R.id.spinner_users)
        val dbFolder = File(applicationContext.filesDir, "db")
        // lista todos os arquivos .db na pasta db
        val dbFiles = dbFolder.listFiles { _, name -> name.endsWith(".db") } ?: arrayOf()
        // extrai apenas os nomes dos arquivos sem extenção
        val userList = dbFiles.map {it.nameWithoutExtension}
        //caso não exista usuários
        val finalList = if (userList.isNotEmpty()) userList else listOf("No users available")
        // cria o adaptador para o spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, finalList)
        // Aplica o adaptador ao Spinner
        spinnerUsers.adapter = adapter
    }

    private fun selectUser() {
        val spinnerUsers = findViewById<Spinner>(R.id.spinner_users)

        // Obtém o nome do usuário selecionado no Spinner
        val selectedUser = spinnerUsers.selectedItem?.toString()

        // Verifica se a seleção é válida
        if (selectedUser == null || selectedUser == "No users available") {
            Toast.makeText(this, "No valid user selected!", Toast.LENGTH_LONG).show()
            return
        }

        // Caminho para o banco de dados do usuário selecionado
        dbPath = File(applicationContext.filesDir, "db/$selectedUser.db")

        // Verifica se o banco de dados realmente existe
        if (!dbPath.exists()) {
            Toast.makeText(this, "Database for $selectedUser not found!", Toast.LENGTH_LONG).show()
            return
        }

        try {
            // Abre o banco de dados para garantir que está íntegro
            val db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READWRITE)

            // Atualiza o TextView para exibir o indicativo do usuário
            val userIndicator = findViewById<TextView>(R.id.user_indicator)
            userIndicator.text = selectedUser

            // Fecha o banco de dados (será reaberto quando necessário)
            db.close()

            // Mensagem de confirmação
            Toast.makeText(this, "User $selectedUser loaded successfully!", Toast.LENGTH_LONG).show()

            // navegar para menu principal
            navigateToPage(3)

        } catch (e: SQLiteException) {
            Toast.makeText(this, "Error loading user: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadContests() {
        val spinnerContests = findViewById<Spinner>(R.id.spinner_contests) // Referência ao Spinner

        // Caminho do banco de dados main.qru
        val dbPath = File(applicationContext.filesDir, "db/main.qru")

        // Verifica se o arquivo existe antes de tentar abrir
        if (!dbPath.exists()) {
            Toast.makeText(this, "Error: Data base main.qru not found!", Toast.LENGTH_LONG).show()
            return
        }

        // Abre o banco de dados em modo somente leitura
        val db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READONLY)

        // Executa a consulta SQL para buscar os nomes dos contests
        val cursor = db.rawQuery("SELECT DisplayName FROM CONTEST", null)

        // Lista para armazenar os nomes dos contests
        val contests = mutableListOf<String>()

        // Percorre o cursor e adiciona os nomes à lista
        if (cursor.moveToFirst()) {
            do {
                val displayName = cursor.getString(0) // Obtém o valor da coluna DisplayName
                contests.add(displayName)
            } while (cursor.moveToNext())
        }

        // Fecha o cursor e o banco de dados
        cursor.close()
        db.close()

        //ordem alfabética
        contests.sort()

        // Configura o Spinner com os dados carregados
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, contests)
        spinnerContests.adapter = adapter

        // inicia os spinners de novo conteste
        setupContestSpinners()
    }



    // Inicializa os componentes
    private fun initializeComponents() {
        viewFlipper = findViewById(R.id.viewFlipper)
    }

    // Configura os botões
    private fun setupButtonListeners() {
        val btnNewUser = findViewById<Button>(R.id.button_new_user)
        btnNewUser.setOnClickListener {
            navigateToPage(1) // vira a página
        }
        val btnCancelLoadUser = findViewById<Button>(R.id.button_cancel_load_user)
        btnCancelLoadUser.setOnClickListener{
            navigateToPage(0)
        }
        val btnCancelNewContest = findViewById<Button>(R.id.button_cancel_new_contest)
        btnCancelNewContest.setOnClickListener {
            navigateToPage(3)
        }

        val btnCancelNewUser = findViewById<Button>(R.id.button_cancel_new_user)
        btnCancelNewUser.setOnClickListener{
            navigateToPage(0)
        }
        val btnOkNewContest = findViewById<Button>(R.id.button_new_contests_ok)
        btnOkNewContest.setOnClickListener {
            createContestInstance() // ativa o botão OK do new contest
        }
    }

    //cria as opções do menu e as faz aparecer.

    private fun setupPopupMenu() {
        val buttonContests = findViewById<Button>(R.id.button_contests)

        buttonContests.setOnClickListener { view ->
            showPopupMenu(view) // Chama o método que exibe o menu
        }
    }

    private fun showPopupMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.contests_menu, popup.menu)

        popup.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.menu_new_contest -> {
                    loadContests()
                    navigateToPage(4)
                }
                R.id.menu_resume_contest -> { /* Ação para continuar conteste */ }
                R.id.menu_export_contest -> { /* Ação para exportar conteste */ }
                R.id.menu_delete_contest -> { /* Ação para deletar conteste */ }
            }
            true
        }
        popup.show()
    }

    //função dos spinners de novo conteste
    private fun setupContestSpinners() {
        findViewById<Spinner>(R.id.spinner_operator).adapter = ArrayAdapter.createFromResource(
            this, R.array.operator, android.R.layout.simple_spinner_dropdown_item
        )
        findViewById<Spinner>(R.id.spinner_band).adapter = ArrayAdapter.createFromResource(
            this, R.array.band, android.R.layout.simple_spinner_dropdown_item
        )
        findViewById<Spinner>(R.id.spinner_power).adapter = ArrayAdapter.createFromResource(
            this, R.array.power, android.R.layout.simple_spinner_dropdown_item
        )
        findViewById<Spinner>(R.id.spinner_mode).adapter = ArrayAdapter.createFromResource(
            this, R.array.mode, android.R.layout.simple_spinner_dropdown_item
        )
        findViewById<Spinner>(R.id.spinner_overlay).adapter = ArrayAdapter.createFromResource(
            this, R.array.overlay, android.R.layout.simple_spinner_dropdown_item
        )
        findViewById<Spinner>(R.id.spinner_station).adapter = ArrayAdapter.createFromResource(
            this, R.array.station, android.R.layout.simple_spinner_dropdown_item
        )
        findViewById<Spinner>(R.id.spinner_assisted).adapter = ArrayAdapter.createFromResource(
            this, R.array.asssisted, android.R.layout.simple_spinner_dropdown_item
        )
        findViewById<Spinner>(R.id.spinner_transmitter).adapter = ArrayAdapter.createFromResource(
            this, R.array.transmitter, android.R.layout.simple_spinner_dropdown_item
        )
        findViewById<Spinner>(R.id.spinner_time_category).adapter = ArrayAdapter.createFromResource(
            this, R.array.time_category, android.R.layout.simple_spinner_dropdown_item
        )
    }
    //instanciamento de novo conteste
    private fun createContestInstance() {
        if (!this::dbPath.isInitialized) {
            Toast.makeText(this, "Database path is not initialized! Select a user first.", Toast.LENGTH_LONG).show()
            return
        }

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

            val spinnerContests = findViewById<Spinner>(R.id.spinner_contests)
            val selectedContest = spinnerContests.selectedItem?.toString() ?: ""

            if (selectedContest.isEmpty()) {
                Toast.makeText(this, "No contest selected!", Toast.LENGTH_LONG).show()
                return
            }

            val mainDbPath = File(applicationContext.filesDir, "db/main.qru")
            if (!mainDbPath.exists()) {
                Toast.makeText(this, "Main contest database not found!", Toast.LENGTH_LONG).show()
                return
            }

            val mainDb = SQLiteDatabase.openDatabase(mainDbPath.path, null, SQLiteDatabase.OPEN_READONLY)
            val cursor = mainDb.rawQuery(
                "SELECT Name, DisplayName, CabrilloName FROM Contest WHERE DisplayName = ?",
                arrayOf(selectedContest)
            )

            if (!cursor.moveToFirst()) {
                Toast.makeText(this, "Contest data not found!", Toast.LENGTH_LONG).show()
                cursor.close()
                mainDb.close()
                return
            }

            val name = cursor.getString(0)
            val displayName = cursor.getString(1)
            val cabrilloName = cursor.getString(2)

            cursor.close()
            mainDb.close()

            // Procedimento de validação dos spinners
            val page = findViewById<ConstraintLayout>(R.id.pag_5)
            val spinners = page.children.filterIsInstance<Spinner>().toList()
            val invalidSelections = spinners
                .map { it.selectedItem?.toString() ?: "" }
                .filter { it.endsWith("?") || it.isEmpty() }

            if (invalidSelections.isNotEmpty()) {
                val message = "These options are invalid: ${invalidSelections.joinToString(", ")}"
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                return
            }

            // procedimento para a validação dos campos de texto
            val editTexts = page.children.filterIsInstance<EditText>().toList()
            val emptyFields = editTexts.filter { it.text.toString().trim().isEmpty() }

            if (emptyFields.isNotEmpty()) {
                val message = "These fields cannot be null: ${emptyFields.joinToString(", ") { it.hint?.toString() ?: "Unnamed" }}"
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                return
            }


            val insertQuery = """
            INSERT INTO Contest (Name, DisplayName, CabrilloName, Operator, Band, Power, Mode, Overlay, Station, Assisted, Transmitter, TimeCategory, SendExchange, Operators) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

            userDb.execSQL(insertQuery, arrayOf(
                name, displayName, cabrilloName,
                findViewById<Spinner>(R.id.spinner_operator).selectedItem.toString(),
                findViewById<Spinner>(R.id.spinner_band).selectedItem.toString(),
                findViewById<Spinner>(R.id.spinner_power).selectedItem.toString(),
                findViewById<Spinner>(R.id.spinner_mode).selectedItem.toString(),
                findViewById<Spinner>(R.id.spinner_overlay).selectedItem.toString(),
                findViewById<Spinner>(R.id.spinner_station).selectedItem.toString(),
                findViewById<Spinner>(R.id.spinner_assisted).selectedItem.toString(),
                findViewById<Spinner>(R.id.spinner_transmitter).selectedItem.toString(),
                findViewById<Spinner>(R.id.spinner_time_category).selectedItem.toString(),
                findViewById<EditText>(R.id.editText_send_exchange).text.toString().trim(),
                findViewById<EditText>(R.id.editText_operators).text.toString().trim()
            ))

            userDb.close()

            Toast.makeText(this, "Contest $displayName created successfully!", Toast.LENGTH_LONG).show()
            navigateToPage(3)

        } catch (e: SQLiteException) {
            Toast.makeText(this, "Error creating contest: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }


    // Lógica para navegar entre páginas
    private fun navigateToPage(pageIndex: Int) {
        viewFlipper.displayedChild = pageIndex
    }
}