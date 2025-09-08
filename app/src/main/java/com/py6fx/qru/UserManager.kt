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
import android.graphics.Color
import android.widget.Button
import android.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView


class UserManager(private val context: Context, private val activity: MainActivity) {

    // Método que lê os dbs que são os usuários e cria uma lista de seleção.

    fun loadUsers(loadUserPage: View) {
        val spinnerUsers = loadUserPage.findViewById<Spinner>(R.id.user_menu_spinner_users)
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

            // Atualiza o userIndicator com o indicativo salvo
            updateUserIndicator(call)

            // Fecha o banco de dados
            db.close()

            // Fecha o banco de dados
            db.close()

            // Mensagem de sucesso
            Toast.makeText(context, "User successfully saved!", Toast.LENGTH_LONG).show()
            activity.navigateToPage(3)


        } catch (e: SQLiteException) {
            Toast.makeText(context, "Error saving user: ${e.message}", Toast.LENGTH_LONG).show()

        }
    }
    // função que seleciona o usuário

    fun selectUser(loadUserPage: View) {
        val spinnerUsers = loadUserPage.findViewById<Spinner>(R.id.user_menu_spinner_users)
        val selectedUser = spinnerUsers.selectedItem?.toString()

        if (selectedUser.isNullOrEmpty() || selectedUser == "No users available") {
            Toast.makeText(context, "No valid user selected!", Toast.LENGTH_LONG).show()
            return
        }

        val dbPath = File(context.filesDir, "db/$selectedUser.db")

        if (!dbPath.exists()) {
            Toast.makeText(context, "Database for $selectedUser not found!", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READWRITE)
            db.close()

            // atualiza o indicador de usuário.
            updateUserIndicator(selectedUser)

            Toast.makeText(context, "User $selectedUser loaded successfully!", Toast.LENGTH_LONG).show()
            activity.navigateToPage(3)
        } catch (e: SQLiteException) {
            Toast.makeText(context, "Error loading user: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    // função do indicador de usuário
    fun updateUserIndicator(call: String) {
        val userIndicator = activity.findViewById<TextView>(R.id.user_indicator)
        userIndicator.text = call
    }
    // ---- Estado de edição de usuário ----
    private var isEditingUser: Boolean = false
    private var editingCall: String? = null
    private var originalCallTextColor: Int? = null

    fun editUser(pag2: View) {
        // 1) Usuário selecionado no User Menu (Spinner)
        val spinner = activity.findViewById<Spinner>(R.id.user_menu_spinner_users)
        val call = spinner.selectedItem?.toString()?.trim().orEmpty()

        if (call.isEmpty() || call == "No users available") {
            Toast.makeText(context, "No valid user selected!", Toast.LENGTH_LONG).show()
            return
        }

        // 2) Abre DB desse usuário e carrega dados
        val dbPath = File(context.filesDir, "db/$call.db")
        if (!dbPath.exists()) {
            Toast.makeText(context, "Database for $call not found!", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READONLY)
            val c = db.rawQuery(
                "SELECT Call, Name, Address, City, State, ZIP, Country, GridSquare, CQZone, ITUZone, ARRLSection, Club, Email " +
                        "FROM user WHERE Call = ? LIMIT 1",
                arrayOf(call)
            )
            if (!c.moveToFirst()) {
                Toast.makeText(context, "User data not found!", Toast.LENGTH_LONG).show()
                c.close(); db.close(); return
            }

            // 3) Preenche formulário da pag_2
            pag2.findViewById<EditText>(R.id.editText_new_user_call).setText(c.getString(c.getColumnIndexOrThrow("Call")))
            pag2.findViewById<EditText>(R.id.editText_new_user_name).setText(c.getString(c.getColumnIndexOrThrow("Name")))
            pag2.findViewById<EditText>(R.id.editText_new_user_address).setText(c.getString(c.getColumnIndexOrThrow("Address")))
            pag2.findViewById<EditText>(R.id.editText_new_user_city).setText(c.getString(c.getColumnIndexOrThrow("City")))
            pag2.findViewById<EditText>(R.id.editText_new_user_state).setText(c.getString(c.getColumnIndexOrThrow("State")))
            pag2.findViewById<EditText>(R.id.editText_new_user_zip).setText(c.getString(c.getColumnIndexOrThrow("ZIP")))
            pag2.findViewById<EditText>(R.id.editText_new_user_country).setText(c.getString(c.getColumnIndexOrThrow("Country")))
            pag2.findViewById<EditText>(R.id.editText_new_user_grid_square).setText(c.getString(c.getColumnIndexOrThrow("GridSquare")))
            pag2.findViewById<EditText>(R.id.editText_new_user_cq_zone).setText(c.getString(c.getColumnIndexOrThrow("CQZone")))
            pag2.findViewById<EditText>(R.id.editText_new_user_itu_zone).setText(c.getString(c.getColumnIndexOrThrow("ITUZone")))
            pag2.findViewById<EditText>(R.id.editText_new_user_arrl_section).setText(c.getString(c.getColumnIndexOrThrow("ARRLSection")))
            pag2.findViewById<EditText>(R.id.editText_new_user_club).setText(c.getString(c.getColumnIndexOrThrow("Club")))
            pag2.findViewById<EditText>(R.id.editText_new_user_email).setText(c.getString(c.getColumnIndexOrThrow("Email")))

            c.close(); db.close()
        } catch (e: SQLiteException) {
            Toast.makeText(context, "Error loading user: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        // 4) Ativa UI de edição: desabilita CALL (vermelho), ajusta rótulos e ações
        isEditingUser = true
        editingCall = call
        setEditUserUi(pag2, active = true)
    }

    private fun setEditUserUi(pag2: View, active: Boolean) {
        val label = activity.findViewById<TextView>(R.id.label_new_user)
        val btnSave = activity.findViewById<Button>(R.id.button_save_user)
        val btnCancel = activity.findViewById<Button>(R.id.button_cancel_new_user)
        val callField = activity.findViewById<EditText>(R.id.editText_new_user_call)

        if (active) {
            label.text = "EDIT USER"

            // CALL fixo em vermelho
            originalCallTextColor = callField.currentTextColor
            callField.isEnabled = false
            callField.setTextColor(Color.RED)

            btnSave.text = "Update User"
            btnSave.setOnClickListener { updateUserInDb(pag2) }

            // Cancel Edit User → volta p/ Main Menu (pag_4) e restaura UI neutra
            btnCancel.text = "Cancel"
            btnCancel.setOnClickListener { cancelEditUser() }
        } else {
            label.text = "NEW USER"

            // Reabilita CALL e cor original
            callField.isEnabled = true
            originalCallTextColor?.let { callField.setTextColor(it) }

            btnSave.text = "Save User"
            btnSave.setOnClickListener { saveUserToDb(pag2) }

            btnCancel.text = "Cancel"
            btnCancel.setOnClickListener { activity.navigateToPage(0) } // comportamento original (pag_1)
        }
    }

    fun updateUserInDb(pag2: View) {
        val call = editingCall ?: run {
            Toast.makeText(context, "No user in edit mode.", Toast.LENGTH_LONG).show()
            return
        }

        // Lê campos (todos menos CALL)
        val name = pag2.findViewById<EditText>(R.id.editText_new_user_name).text.toString().trim()
        val address = pag2.findViewById<EditText>(R.id.editText_new_user_address).text.toString().trim()
        val city = pag2.findViewById<EditText>(R.id.editText_new_user_city).text.toString().trim()
        val state = pag2.findViewById<EditText>(R.id.editText_new_user_state).text.toString().trim()
        val zip = pag2.findViewById<EditText>(R.id.editText_new_user_zip).text.toString().trim()
        val country = pag2.findViewById<EditText>(R.id.editText_new_user_country).text.toString().trim()
        val grid = pag2.findViewById<EditText>(R.id.editText_new_user_grid_square).text.toString().trim()
        val cq = pag2.findViewById<EditText>(R.id.editText_new_user_cq_zone).text.toString().trim()
        val itu = pag2.findViewById<EditText>(R.id.editText_new_user_itu_zone).text.toString().trim()
        val arrl = pag2.findViewById<EditText>(R.id.editText_new_user_arrl_section).text.toString().trim()
        val club = pag2.findViewById<EditText>(R.id.editText_new_user_club).text.toString().trim()
        val email = pag2.findViewById<EditText>(R.id.editText_new_user_email).text.toString().trim()

        // (Validação simples — opcional: pode reutilizar a do save)
        val missing = listOf(
            "Name" to name, "Address" to address, "City" to city, "State" to state, "ZIP" to zip,
            "Country" to country, "Grid Square" to grid, "CQ Zone" to cq, "ITU Zone" to itu,
            "ARRL Section" to arrl, "Club" to club, "Email" to email
        ).filter { it.second.isEmpty() }.map { it.first }
        if (missing.isNotEmpty()) {
            Toast.makeText(context, "Os seguintes campos estão vazios: ${missing.joinToString(", ")}", Toast.LENGTH_LONG).show()
            return
        }

        val dbPath = File(context.filesDir, "db/$call.db")
        if (!dbPath.exists()) {
            Toast.makeText(context, "Database for $call not found!", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val db = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READWRITE)
            val update = """
            UPDATE user SET
                Name=?, Address=?, City=?, State=?, ZIP=?, Country=?, GridSquare=?,
                CQZone=?, ITUZone=?, ARRLSection=?, Club=?, Email=?
            WHERE Call=?
        """.trimIndent()
            db.execSQL(update, arrayOf(name, address, city, state, zip, country, grid, cq, itu, arrl, club, email, call))
            db.close()

            Toast.makeText(context, "User updated successfully!", Toast.LENGTH_LONG).show()

            // Sai do modo edição e volta p/ Main Menu (pag_4)
            isEditingUser = false
            editingCall = null
            setEditUserUi(pag2, active = false)
            clearUserForm(pag2) // deixa no estado neutro
            activity.navigateToPage(2)
        } catch (e: SQLiteException) {
            Toast.makeText(context, "Error updating user: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun cancelEditUser() {
        if (!isEditingUser) { activity.navigateToPage(3); return }

        val pag2 = activity.findViewById<View>(R.id.pag_2)
        // Restaura UI neutra, reabilita CALL, zera flags e limpa campos
        isEditingUser = false
        editingCall = null
        setEditUserUi(pag2, active = false)
        clearUserForm(pag2)

        // >>> AÇÃO SOLICITADA: apenas voltar ao Main Menu (pag_4)
        activity.navigateToPage(3)
    }

    // Utilitário: limpa todos os campos da pag_2
    private fun clearUserForm(pag2: View) {
        listOf(
            R.id.editText_new_user_call,
            R.id.editText_new_user_name,
            R.id.editText_new_user_address,
            R.id.editText_new_user_city,
            R.id.editText_new_user_state,
            R.id.editText_new_user_zip,
            R.id.editText_new_user_country,
            R.id.editText_new_user_grid_square,
            R.id.editText_new_user_arrl_section,
            R.id.editText_new_user_club,
            R.id.editText_new_user_cq_zone,
            R.id.editText_new_user_itu_zone,
            R.id.editText_new_user_email
        ).forEach { id -> pag2.findViewById<EditText>(id).setText("") }
    }
    fun deleteUser(pag3: View) {
        val spinnerUsers = pag3.findViewById<Spinner>(R.id.user_menu_spinner_users)
        val selectedUser = spinnerUsers.selectedItem?.toString()

        if (selectedUser.isNullOrEmpty() || selectedUser == "No users available") {
            Toast.makeText(context, "No valid user selected!", Toast.LENGTH_LONG).show()
            return
        }

        AlertDialog.Builder(context)
            .setTitle("Delete User")
            .setMessage("Are you sure you want to delete user $selectedUser?")
            .setPositiveButton("Delete") { _, _ ->
                val userDbPath = File(context.filesDir, "db/$selectedUser.db")
                try {
                    // Tenta apagar o arquivo .db (hard delete)
                    if (userDbPath.exists()) {
                        val ok = userDbPath.delete()
                        if (!ok) {
                            Toast.makeText(context, "Error deleting user: unable to delete file.", Toast.LENGTH_LONG).show()
                            return@setPositiveButton
                        }
                    } // se não existe, tratamos como deletado

                    // Se o usuário apagado era o ativo, resetar indicadores e estados
                    val userIndicator = activity.findViewById<TextView>(R.id.user_indicator)
                    val wasActive = userIndicator.text.toString().trim() == selectedUser
                    if (wasActive) {
                        // Reset indicadores
                        userIndicator.text = "USER?"
                        activity.findViewById<TextView?>(R.id.contest_indicator)?.text = "No contest"

                        // Limpar memórias e UI relacionadas
                        val logger = LoggerManager()
                        logger.clearAllMemories(activity)

                        // Esconder banner de DUPE (se visível)
                        activity.hideDupeBanner()

                        // Se estiver na tela do Logger, esvaziar a lista e voltar ao Main Menu
                        if (activity.viewFlipper.displayedChild == 7) {
                            val recycler = activity.findViewById<RecyclerView>(R.id.recyclerViewQSOs)
                            recycler.adapter = QsoLogAdapter(emptyList()) { /* no-op */ }
                        }
                        activity.navigateToPage(3) // Main Menu
                    }

                    // Recarregar o spinner de usuários
                    loadUsers(pag3)

                    Toast.makeText(context, "User $selectedUser deleted successfully!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error deleting user: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

}