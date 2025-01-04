package com.py6fx.qru

import android.os.Bundle
import android.widget.Button
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Conecta o ViewFlipper
        val viewFlipper = findViewById<ViewFlipper>(R.id.viewFlipper)

        // Botão da Página 2 New User
        val btnNewUser = findViewById<Button>(R.id.button_new_user)
        btnNewUser.setOnClickListener {
            val pageIndex = 1
            viewFlipper.displayedChild = pageIndex
        }

        // Botão da Página 2 → Página Anterior
        val btnPrevPage2 = findViewById<Button>(R.id.btnPrevPage2)
        btnPrevPage2.setOnClickListener {
            viewFlipper.showPrevious()
        }
    }
}




