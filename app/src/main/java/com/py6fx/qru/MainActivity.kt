package com.py6fx.qru

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

class MainActivity : AppCompatActivity() {

    // Declaração de variável para o ViewFlipper
    private lateinit var viewFlipper: ViewFlipper

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
        val btnCancelNewUser = findViewById<Button>(R.id.button_cancel_new_user)
        btnCancelNewUser.setOnClickListener{
            navigateToPage(0)
        }
    }

    // Lógica para navegar entre páginas
    private fun navigateToPage(pageIndex: Int) {
        viewFlipper.displayedChild = pageIndex
    }
}