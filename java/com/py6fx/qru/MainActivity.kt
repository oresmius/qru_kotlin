package com.py6fx.qru

import android.os.Bundle
import android.widget.Button
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
