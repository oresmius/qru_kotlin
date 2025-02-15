package com.py6fx.qru

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
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

        buttonSaveUser.setOnClickListener {
            // Coleta os dados dos campos EditText
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

            // Validação: Verifica se algum campo está vazio
            val missingFields = mutableListOf<String>()
            if (call.isEmpty()) missingFields.add("Call")
            if (name.isEmpty()) missingFields.add("Name")
            if (address.isEmpty()) missingFields.add("Address")
            if (city.isEmpty()) missingFields.add("City")
            if (state.isEmpty()) missingFields.add("State")
            if (zip.isEmpty()) missingFields.add("ZIP")
            if (country.isEmpty()) missingFields.add("Country")
            if (grid.isEmpty()) missingFields.add("Grid Square")
            if (cq.isEmpty()) missingFields.add("CQ Zone")
            if (itu.isEmpty()) missingFields.add("ITU Zone")
            if (arrl.isEmpty()) missingFields.add("ARRL Section")
            if (club.isEmpty()) missingFields.add("Club")
            if (email.isEmpty()) missingFields.add("Email")

            // Exibe um alerta se houver campos vazios
            if (missingFields.isNotEmpty()) {
                val message = "Os seguintes campos estão vazios: ${missingFields.joinToString(", ")}"
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                return@setOnClickListener // Cancela o salvamento
            }

            // Proxima etapa será salvar no banco
        }

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
