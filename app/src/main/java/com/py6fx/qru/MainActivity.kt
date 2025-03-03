package com.py6fx.qru

import android.os.Bundle
import android.widget.Button
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity
import java.io.File
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
        contestManager = ContestManager(this, this)

        // Inicializa o UserManager
        userManager = UserManager(this, this)

        // Inicializa componentes
        initializeComponents()

        // Configura os botões
        setupButtonListeners()

        // Inicializa menus popup
        setupPopupMenu()
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
            userManager.selectUser(findViewById(R.id.pag_3))
        }
        findViewById<Button>(R.id.button_cancel_new_user).setOnClickListener {
            navigateToPage(0)
        }

        // Configuração do botão "Load User"
        findViewById<Button>(R.id.button_load_user).setOnClickListener {
            userManager.loadUsers(findViewById(R.id.pag_3))
            navigateToPage(2)
        }
        //configuração do botão "save user"
        findViewById<Button>(R.id.button_save_user).setOnClickListener {
            userManager.saveUserToDb(findViewById(R.id.pag_2))
        }

        // Chamar a função correta do ContestManager
        findViewById<Button>(R.id.button_new_contests_ok).setOnClickListener {
            contestManager.createContestInstance(findViewById(R.id.pag_5), File(dbPath, "user.qru"))

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
                    contestManager.loadContests(findViewById(R.id.pag_5))
                    navigateToPage(4)
                }
                R.id.menu_resume_contest -> {
                    contestManager.loadInitializedContests(findViewById(R.id.pag_6))
                    navigateToPage(5)
                }
                R.id.menu_export_contest -> { /* Ação para exportar conteste */ }
                R.id.menu_delete_contest -> { /* Ação para deletar conteste */ }
            }
            true
        }
        popup.show()
    }

    // Lógica para navegar entre páginas
    fun navigateToPage(pageIndex: Int) {
        viewFlipper.displayedChild = pageIndex
    }
}
