package com.py6fx.qru

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    // Declaração de variável para o ViewFlipper
    private lateinit var viewFlipper: ViewFlipper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Verifica e cria a pasta "db" se não existir
        val dbFolder = File(applicationContext.filesDir , "db")
        if (!dbFolder.exists()) {
            dbFolder.mkdirs()
        }

        // Inicializa componentes
        initializeComponents()

        // Configura os botões
        setupButtonListeners()

        // referência ao botão de salvar
        val buttonSaveUser = findViewById<Button>(R.id.button_save_user)

        // Referências aos campos de editText
        val editTextNewUserCall = findViewById<EditText>(R.id.editText_new_user_call)
        val editTextNewUserName = findViewById<EditText>(R.id.editText_new_user_name)
        val editTextNewUserAddress = findViewById<EditText>(R.id.editText_new_user_address)
        val editTextNewUserCity = findViewById<EditText>(R.id.editText_new_user_city)
        val editTextNewUserState = findViewById<EditText>(R.id.editText_new_user_state)
        val editTextNewUserZip = findViewById<EditText>(R.id.editText_new_user_zip)
        val editTextNewUserCountry = findViewById<EditText>(R.id.editText_new_user_country)
        val editTextNewUserGridSquare = findViewById<EditText>(R.id.editText_new_user_grid_square)
        val editTextNewUserArrlSection = findViewById<EditText>(R.id.editText_new_user_arrl_section)
        val editTextNewUserClub = findViewById<EditText>(R.id.editText_new_user_club)
        val editTextNewUserCqZone = findViewById<EditText>(R.id.editText_new_user_cq_zone)
        val editTextNewUserItuZone = findViewById<EditText>(R.id.editText_new_user_itu_zone)
        val editTextNewUserEmail = findViewById<EditText>(R.id.editText_new_user_email)
    }

    // Inicializa os componentes
    private fun initializeComponents() {
        viewFlipper = findViewById(R.id.viewFlipper)
    }

    // Configura os botões
    private fun setupButtonListeners() {
        val btnNewUser = findViewById<Button>(R.id.button_new_user)
        btnNewUser.setOnClickListener {
            navigateToPage(1) // Move a lógica para um método específico
        }
    }

    // Lógica para navegar entre páginas
    private fun navigateToPage(pageIndex: Int) {
        viewFlipper.displayedChild = pageIndex
    }
}
