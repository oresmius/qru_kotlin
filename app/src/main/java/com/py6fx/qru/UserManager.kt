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
import java.io.File
import java.util.concurrent.atomic.LongAdder

class UserManager(private val context: Context, private val activity: MainActivity) {

    // Método que lê os dbs que são os usuários e cria uma lista de seleção.

    fun loadUsers(loadUserPage: View) {
        val spinnerUsers = loadUserPage.findViewById<Spinner>(R.id.spinner_users)
        val dbFolder = File(context.filesDir, "db")
        val dbFiles = dbFolder.listFiles { _, name -> name.endsWith(".db") } ?: arrayOf()
        val userList = dbFiles.map { it.nameWithoutExtension }
        val finalList = if (userList.isNotEmpty()) userList else listOf("No users available")
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, finalList)
        spinnerUsers.adapter = adapter
    }
    // método que salva os dados do usuário no bd
    fun saveUserToDb(newUserPage: View) {

        // referências aos campos de editText

        val editTextCall = newUserPage.findViewById<EditText>(R.id.editText_new_user_call)
        val editTextName = newUserPage.findViewById<EditText>(R.id.editText_new_user_name)
        val editTextAddress = newUserPage.findViewById<EditText>(R.id.editText_new_user_address)
        val editTextCity = newUserPage.findViewById<EditText>(R.id.editText_new_user_city)
        val editTextState = newUserPage.findViewById<EditText>(R.id.editText_new_user_state)
        val editTextZIP = newUserPage.findViewById<EditText>(R.id.editText_new_user_zip)
        val editTextCountry = newUserPage.findViewById<EditText>(R.id.editText_new_user_country)
        val editTextGrid = newUserPage.findViewById<EditText>(R.id.editText_new_user_grid_square)
        val editTextARRL = newUserPage.findViewById<EditText>(R.id.editText_new_user_arrl_section)
        val editTextClub = newUserPage.findViewById<EditText>(R.id.editText_new_user_club)
        val editTextCQ = newUserPage.findViewById<EditText>(R.id.editText_new_user_cq_zone)
        val editTextITU = newUserPage.findViewById<EditText>(R.id.editText_new_user_itu_zone)
        val editTextEmail = newUserPage.findViewById<EditText>(R.id.editText_new_user_email)

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
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            return
        }

        // caminho para salvar o bd na pasta "db"
        val dbPath = File(context.filesDir, "db/$call.db")

        // Verifica se o banco já existe
        if (dbPath.exists()) {
            Toast.makeText(context, "A database with this name already exists. Please choose a different call sign.", Toast.LENGTH_LONG).show()
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
            Toast.makeText(context, "User successfully saved!", Toast.LENGTH_LONG).show()
            activity.navigateToPage(3)

            val userIndicator = newUserPage.findViewById<TextView>(R.id.user_indicator)
            userIndicator.text = call

        } catch (e: SQLiteException) {
            Toast.makeText(context, "Error saving user: ${e.message}", Toast.LENGTH_LONG).show()

        }
    }
}