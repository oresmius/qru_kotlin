package com.py6fx.qru

import android.os.Bundle
import android.widget.Button
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


class MainActivity : AppCompatActivity() {

    // Declaração universal de variável para o ViewFlipper
    private lateinit var viewFlipper: ViewFlipper

    // Declaração universal do banco de dados em uso
    private lateinit var dbPath: File

    // Instância do ContestManager
    private lateinit var contestManager: ContestManager

    // Instância do UserManager
    private lateinit var userManager: UserManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Verifica e cria a pasta "db" se não existir
        val dbFolder = File(applicationContext.filesDir, "db")
        if (!dbFolder.exists()) {
            dbFolder.mkdirs()
        }

        // Inicializa o caminho do banco de dados
        dbPath = File(applicationContext.filesDir, "db")

        // Inicializa o ContestManager
        contestManager = ContestManager(this)

        // Inicializa o UserManager
        userManager = UserManager(this)

        // Inicializa componentes
        initializeComponents()

        // Configura os botões
        setupButtonListeners()

        // Inicializa menus popup
        setupPopupMenu()
    }

    // Método que lê os dbs que são os usuários e cria uma lista de seleção.
    private fun loadUsers() {
        val spinnerUsers = findViewById<Spinner>(R.id.spinner_users)
        val dbFolder = File(applicationContext.filesDir, "db")
        val dbFiles = dbFolder.listFiles { _, name -> name.endsWith(".db") } ?: arrayOf()
        val userList = dbFiles.map { it.nameWithoutExtension }
        val finalList = if (userList.isNotEmpty()) userList else listOf("No users available")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, finalList)
        spinnerUsers.adapter = adapter
    }

    private fun selectUser() {
        val spinnerUsers = findViewById<Spinner>(R.id.spinner_users)
        val selectedUser = spinnerUsers.selectedItem?.toString()

        if (selectedUser == null || selectedUser == "No users available") {
            Toast.makeText(this, "No valid user selected!", Toast.LENGTH_LONG).show()
            return
        }

        dbPath = File(applicationContext.filesDir, "db/$selectedUser.db")

        if (!dbPath.exists()) {
            Toast.makeText(this, "Database for $selectedUser not found!", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READWRITE)
            val userIndicator = findViewById<TextView>(R.id.user_indicator)
            userIndicator.text = selectedUser
            db.close()
            Toast.makeText(this, "User $selectedUser loaded successfully!", Toast.LENGTH_LONG).show()
            navigateToPage(3)
        } catch (e: SQLiteException) {
            Toast.makeText(this, "Error loading user: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadContests() {
        val spinnerContests = findViewById<Spinner>(R.id.spinner_contests)
        val dbPath = File(applicationContext.filesDir, "db/main.qru")

        if (!dbPath.exists()) {
            Toast.makeText(this, "Error: Database main.qru not found!", Toast.LENGTH_LONG).show()
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

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, contests)
        spinnerContests.adapter = adapter

        setupContestSpinners()
    }

    // Inicializa os componentes
    private fun initializeComponents() {
        viewFlipper = findViewById(R.id.viewFlipper)
    }

    // Configura os botões
    private fun setupButtonListeners() {
        findViewById<Button>(R.id.button_new_user).setOnClickListener {
            navigateToPage(1)
        }
        findViewById<Button>(R.id.button_cancel_load_user).setOnClickListener {
            navigateToPage(0)
        }
        findViewById<Button>(R.id.button_cancel_new_contest).setOnClickListener {
            navigateToPage(3)
        }
        val buttonSelectUser = findViewById<Button>(R.id.button_select_user)
        buttonSelectUser.setOnClickListener {
            selectUser()
        }
        findViewById<Button>(R.id.button_cancel_new_user).setOnClickListener {
            navigateToPage(0)
        }

        // Configuração do botão "Load User"
        findViewById<Button>(R.id.button_load_user).setOnClickListener {
            loadUsers()
            navigateToPage(2)
        }
        //configuração do botão "save user"
        findViewById<Button>(R.id.button_save_user).setOnClickListener {
            userManager.saveUserToDb(findViewById(R.id.pag_2))
        }

        // Chamar a função correta do ContestManager
        findViewById<Button>(R.id.button_new_contests_ok).setOnClickListener {
            contestManager.createContestInstance(findViewById(R.id.pag_5), dbPath)
        }
    }

    // Cria as opções do menu e as faz aparecer
    private fun setupPopupMenu() {
        val buttonContests = findViewById<Button>(R.id.button_contests)
        buttonContests.setOnClickListener { view -> showPopupMenu(view) }
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

    // Função dos spinners de novo conteste
    private fun setupContestSpinners() {
        findViewById<Spinner>(R.id.spinner_operator).adapter =
            ArrayAdapter.createFromResource(this, R.array.operator, android.R.layout.simple_spinner_dropdown_item)

        findViewById<Spinner>(R.id.spinner_band).adapter =
            ArrayAdapter.createFromResource(this, R.array.band, android.R.layout.simple_spinner_dropdown_item)

        findViewById<Spinner>(R.id.spinner_power).adapter =
            ArrayAdapter.createFromResource(this, R.array.power, android.R.layout.simple_spinner_dropdown_item)

        findViewById<Spinner>(R.id.spinner_mode).adapter =
            ArrayAdapter.createFromResource(this, R.array.mode, android.R.layout.simple_spinner_dropdown_item)

        findViewById<Spinner>(R.id.spinner_overlay).adapter =
            ArrayAdapter.createFromResource(this, R.array.overlay, android.R.layout.simple_spinner_dropdown_item)

        findViewById<Spinner>(R.id.spinner_station).adapter =
            ArrayAdapter.createFromResource(this, R.array.station, android.R.layout.simple_spinner_dropdown_item)

        findViewById<Spinner>(R.id.spinner_assisted).adapter =
            ArrayAdapter.createFromResource(this, R.array.asssisted, android.R.layout.simple_spinner_dropdown_item)

        findViewById<Spinner>(R.id.spinner_transmitter).adapter =
            ArrayAdapter.createFromResource(this, R.array.transmitter, android.R.layout.simple_spinner_dropdown_item)

        findViewById<Spinner>(R.id.spinner_time_category).adapter =
            ArrayAdapter.createFromResource(this, R.array.time_category, android.R.layout.simple_spinner_dropdown_item)
    }

    // Lógica para navegar entre páginas
    private fun navigateToPage(pageIndex: Int) {
        viewFlipper.displayedChild = pageIndex
    }
}
