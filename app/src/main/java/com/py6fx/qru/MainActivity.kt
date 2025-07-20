package com.py6fx.qru

import android.os.Bundle
import android.widget.Button
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.EditText

class MainActivity : AppCompatActivity() {

    // Declaração universal de variável para o ViewFlipper
    private lateinit var viewFlipper: ViewFlipper

    //teste do bluetotth
    private lateinit var SimpleBluetooth: SimpleBluetooth

    // Declaração universal do banco de dados em uso
    private lateinit var dbPath: File

    // Instância do ContestManager
    private lateinit var contestManager: ContestManager

    // Instância do UserManager
    private lateinit var userManager: UserManager

    // Instância do Btmanager
    private lateinit var btManager: BtManager

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

        // inicia o BtManager
        val deviceContainer = findViewById<LinearLayout>(R.id.deviceContainer)
        btManager = BtManager(
            this,
            this,
            deviceContainer,
            onQrgUpdate = { qrg ->
                findViewById<TextView>(R.id.qrg_indicator).text = qrg
            },
            onModeUpdate = { modo ->
                findViewById<TextView>(R.id.mode_indicator).text = modo

                // Só atualiza o RST se estiver na tela de logger (pag_8, index 7)
                if (viewFlipper.displayedChild == 7) {
                    val editTextTX = findViewById<EditText>(R.id.editText_TX_RST)
                    val editTextRX = findViewById<EditText>(R.id.editText_RX_RST)
                    LoggerManager().RSTAutomatico(modo, editTextTX, editTextRX)
                }
            }

        )

        // inicia o teste simplebluetooth
        SimpleBluetooth = SimpleBluetooth(this)

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
            contestManager.resetContestForm()
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

        // Chamar a função correta do ContestManager com o banco do usuário ativo
        findViewById<Button>(R.id.button_new_contests_ok).setOnClickListener {
            val userIndicator = findViewById<TextView>(R.id.user_indicator).text.toString().trim()
            val userDbPath = File(dbPath, "$userIndicator.db") // Agora usa o banco correto
            contestManager.createContestInstance(findViewById(R.id.pag_5), userDbPath)
        }

        // chamar a função resumeContest
        findViewById<Button>(R.id.button_bluetooth).setOnClickListener {
            navigateToPage(6)
            val deviceContainer = findViewById<LinearLayout>(R.id.deviceContainer)
            btManager = BtManager(
                this,
                this,
                deviceContainer,
                onQrgUpdate = { qrg ->
                    findViewById<TextView>(R.id.qrg_indicator).text = qrg
                },
                onModeUpdate = { modo ->
                    findViewById<TextView>(R.id.mode_indicator).text = modo
                    if (viewFlipper.displayedChild == 7) {
                        val editTextTX = findViewById<EditText>(R.id.editText_TX_RST)
                        val editTextRX = findViewById<EditText>(R.id.editText_RX_RST)
                        LoggerManager().RSTAutomatico(modo, editTextTX, editTextRX)
                    }
                }
            )

            btManager.loadPairedDevices()
        }

        findViewById<Button>(R.id.button_select_bluetooth).setOnClickListener {
            btManager.connectToDevice()
            navigateToPage(3)
        }

        findViewById<Button>(R.id.button_cancel_bluetooth).setOnClickListener {
            SimpleBluetooth.connectToDevice()
        }

        findViewById<Button>(R.id.button_Logger).setOnClickListener {
            navigateToPage(7)
            // Aguarda a interface ser atualizada antes de acessar os campos
            findViewById<ViewFlipper>(R.id.viewFlipper).post {
                val modo = findViewById<TextView>(R.id.mode_indicator).text.toString().trim()
                val editTextTX = findViewById<EditText>(R.id.editText_TX_RST)
                val editTextRX = findViewById<EditText>(R.id.editText_RX_RST)
                LoggerManager().RSTAutomatico(modo, editTextTX, editTextRX)
            }
        }

        findViewById<Button>(R.id.button_resume_contest).setOnClickListener {
            contestManager.resumeContest(findViewById(R.id.pag_6))
        }
        findViewById<Button>(R.id.button_edit_contest).setOnClickListener {
            contestManager.editContest(findViewById(R.id.pag_6))
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
                    contestManager.resetContestForm()
                    contestManager.loadContests(findViewById(R.id.pag_5))
                    navigateToPage(4)
                }
                R.id.menu_resume_contest -> {
                    contestManager.loadInitializedContests(findViewById(R.id.pag_6))
                    navigateToPage(5)
                }
                R.id.menu_edit_contest -> {
                    contestManager.loadInitializedContests(findViewById(R.id.pag_6))
                    navigateToPage(5)
                }
                R.id.menu_export_contest -> {
                    contestManager.loadInitializedContests(findViewById(R.id.pag_6))
                    navigateToPage(5)
                }
                R.id.menu_delete_contest -> {
                    contestManager.loadInitializedContests(findViewById(R.id.pag_6))
                    navigateToPage(5)
                }
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