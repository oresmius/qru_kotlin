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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Spinner
import android.widget.Toast
import android.util.DisplayMetrics
import android.view.inputmethod.InputMethodManager


class MainActivity : AppCompatActivity() {

    private var lastQrgText: String? = null
    private var lastModeText: String? = null
    private var callWatcher: android.text.TextWatcher? = null

    // --- DUPE banner state ---
    private var lastDupeQsoId: Long? = null

    // Views do aviso e da lista
    private lateinit var dupeBanner: TextView
    private lateinit var qsoRecycler: RecyclerView

    private lateinit var exportador: ExportCabrilloManager

    // Declaração universal de variável para o ViewFlipper
    lateinit var viewFlipper: ViewFlipper

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

    //instância do logger manager
    private val logger = LoggerManager()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Verifica e cria a pasta "db" se não existir
        val dbFolder = File(applicationContext.filesDir, "db")
        if (!dbFolder.exists()) {
            dbFolder.mkdirs()
        }

        val targetFile = File(dbFolder, "main.qru")
        if (!targetFile.exists()) {
            assets.open("main.qru").use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
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
                val qrgView = findViewById<TextView>(R.id.qrg_indicator)
                val old = lastQrgText
                qrgView.text = qrg
                if (old == null || old != qrg) {   // só limpa se mudou de fato
                    lastQrgText = qrg
                    hideDupeBanner()
                }
                logger.updateMemorySuggestionForCurrentQrg(this)
            },
            onModeUpdate = { modo ->
                val modeView = findViewById<TextView>(R.id.mode_indicator)
                val old = lastModeText
                modeView.text = modo
                if (old == null || old != modo) {  // só limpa se mudou de fato
                    lastModeText = modo
                    hideDupeBanner()
                }
                if (viewFlipper.displayedChild == 7) {
                    val editTextTX = findViewById<EditText>(R.id.editText_TX_RST)
                    val editTextRX = findViewById<EditText>(R.id.editText_RX_RST)
                    logger.RSTAutomatico(modo, editTextTX, editTextRX)
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

        //exportador do Cabrillo

        exportador = ExportCabrilloManager(this)
        exportador.registrarExportador { uri ->
            if (uri != null) {
                val conteudo = exportador.gerarConteudoCabrillo("NomeDoContest")
                if (conteudo.isNotEmpty()) {
                    exportador.salvarCabrillo(uri, conteudo)
                }
            }
        }

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
            val userIndicator = findViewById<TextView>(R.id.user_indicator)
            val before = userIndicator.text.toString()

            userManager.selectUser(findViewById(R.id.pag_3))

            // Verifica se o usuário realmente mudou; se sim, limpa memórias e a sugestão visual
            userIndicator.post {
                val after = userIndicator.text.toString()
                if (after.isNotEmpty() && after != before && after != "No users available") {
                    logger.clearAllMemories(this)
                }
            }
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
            val contestIndicator = findViewById<TextView>(R.id.contest_indicator)
            val before = contestIndicator.text.toString()

            val userIndicator = findViewById<TextView>(R.id.user_indicator).text.toString().trim()
            val userDbPath = File(dbPath, "$userIndicator.db")
            contestManager.createContestInstance(findViewById(R.id.pag_5), userDbPath)

            // Se o contest ativo mudou, limpa memórias e a sugestão visual
            contestIndicator.post {
                val after = contestIndicator.text.toString()
                if (after.isNotEmpty() && after != before) {
                    logger.clearAllMemories(this)
                }
            }
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
                    val qrgView = findViewById<TextView>(R.id.qrg_indicator)
                    val old = lastQrgText
                    qrgView.text = qrg
                    if (old == null || old != qrg) {   // só limpa se mudou de fato
                        lastQrgText = qrg
                        hideDupeBanner()
                    }
                    logger.updateMemorySuggestionForCurrentQrg(this)
                },
                onModeUpdate = { modo ->
                    val modeView = findViewById<TextView>(R.id.mode_indicator)
                    val old = lastModeText
                    modeView.text = modo
                    if (old == null || old != modo) {  // só limpa se mudou de fato
                        lastModeText = modo
                        hideDupeBanner()
                    }
                    if (viewFlipper.displayedChild == 7) {
                        val editTextTX = findViewById<EditText>(R.id.editText_TX_RST)
                        val editTextRX = findViewById<EditText>(R.id.editText_RX_RST)
                        logger.RSTAutomatico(modo, editTextTX, editTextRX)
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
            findViewById<ViewFlipper>(R.id.viewFlipper).post {
                // --- Seu código antigo
                logger.preencherTXExch(this)

                val modo = findViewById<TextView>(R.id.mode_indicator).text.toString().trim()
                val editTextTX = findViewById<EditText>(R.id.editText_TX_RST)
                val editTextRX = findViewById<EditText>(R.id.editText_RX_RST)
                logger.RSTAutomatico(modo, editTextTX, editTextRX)

                val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewQSOs)
                recyclerView.layoutManager = LinearLayoutManager(this)

// Busca os QSOs
                val listaQsos = logger.obterQsosDoContestAtual(this)
                val adapter = QsoLogAdapter(listaQsos) { item ->
                    logger.entrarEditMode(
                        activity = this,
                        qsoId = item.id,
                        rxCall = item.rxCall,
                        rxRst = item.rxRst,
                        rxNr = item.rxNr,
                        rxExch = item.rxExch,
                        txRst = item.txRst,
                        txExch = item.txExch
                    )
                }
                recyclerView.adapter = adapter
                logger.updateMemorySuggestionForCurrentQrg(this)
            }
            // --- Verificação de DUPE em tempo real (a cada tecla) ---
            val etCall = findViewById<EditText>(R.id.editText_RX_Call)

// Remova watcher antigo (se existir) para não ter duplicata
            callWatcher?.let { etCall.removeTextChangedListener(it) }

            callWatcher = object : android.text.TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (viewFlipper.displayedChild != 7) return
                    if (LoggerManager.isEditing) return   // <- evita DUPE durante edição

                    val partial = s?.toString()?.trim()
                    // IMPORTANTE: não mexa no banner até ter 4+ chars; se zerou, aí sim esconda
                    if (partial.isNullOrEmpty()) {
                        hideDupeBanner(); return
                    }
                    if (partial.length < 4) return

                    val qrg = findViewById<TextView>(R.id.qrg_indicator).text.toString().trim()
                    val modo = findViewById<TextView>(R.id.mode_indicator).text.toString().trim()

                    val result = logger.checkDupeRealtime(
                        activity = this@MainActivity,
                        currentQrg = qrg,
                        currentMode = modo,
                        partialCall = partial
                    )

                    if (result.isDupe) showDupeBannerFor(result.qsoId) else hideDupeBanner()
                }

                override fun afterTextChanged(s: android.text.Editable?) {}
            }
            etCall.addTextChangedListener(callWatcher)
        }

        findViewById<Button>(R.id.button_resume_contest).setOnClickListener {
            val contestIndicator = findViewById<TextView>(R.id.contest_indicator)
            val before = contestIndicator.text.toString()

            contestManager.resumeContest(findViewById(R.id.pag_6))

            contestIndicator.post {
                val after = contestIndicator.text.toString()
                if (after.isNotEmpty() && after != before) {
                    logger.clearAllMemories(this)
                }
            }
        }

        findViewById<Button>(R.id.button_edit_contest).setOnClickListener {
            contestManager.editContest(findViewById(R.id.pag_6))
        }

        //chamada função de log
        findViewById<Button>(R.id.button_log_QSO).setOnClickListener {
            if (LoggerManager.isEditing) {
                val ok = logger.atualizarQSO(this)
                if (ok) {
                    val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewQSOs)
                    val listaAtualizada = logger.obterQsosDoContestAtual(this)
                    recyclerView.adapter = QsoLogAdapter(listaAtualizada) { item ->
                        logger.entrarEditMode(
                            this, item.id, item.rxCall, item.rxRst,
                            item.rxNr, item.rxExch, item.txRst, item.txExch
                        )
                    }
                    logger.preencherTXExch(this)
                }
            } else {

                // Loga (se for DUPE, apenas mostra o banner; o INSERT ocorre do mesmo jeito)
                logger.logQSO(this)

                // SEMPRE limpar e atualizar a lista — DUPE se comporta como QSO normal
                logger.limparCamposQSO(this)
                logger.preencherTXExch(this)

                val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewQSOs)
                val listaAtualizada = logger.obterQsosDoContestAtual(this)
                recyclerView.adapter = QsoLogAdapter(listaAtualizada) { item ->
                    logger.entrarEditMode(
                        this, item.id, item.rxCall, item.rxRst,
                        item.rxNr, item.rxExch, item.txRst, item.txExch
                    )
                }
            }
        }

        findViewById<Button>(R.id.button_export_cabrillo_contest).setOnClickListener {
            val spinner = findViewById<Spinner>(R.id.spinner_contests_initialized)
            val selectedItem = spinner.selectedItem?.toString()

            if (selectedItem.isNullOrEmpty()) {
                Toast.makeText(this, "No contest selected!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val startTime = selectedItem.substringBefore(" - ").trim()

            exportador.iniciarExportacaoCabrillo(startTime)
        }

        findViewById<Button>(R.id.button_export_adif_contest).setOnClickListener {
            val spinner = findViewById<Spinner>(R.id.spinner_contests_initialized)
            val selectedItem = spinner.selectedItem?.toString()

            if (selectedItem.isNullOrEmpty()) {
                Toast.makeText(this, "No contest selected!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val startTime = selectedItem.substringBefore(" - ").trim()

            exportador.iniciarExportacaoAdif(startTime)
        }

        findViewById<Button>(R.id.button_log_cancel).setOnClickListener {
            if (LoggerManager.isEditing) {
                logger.cancelarEdicao(this)
            } else {
                navigateToPage(3)
            }
        }
        // Label do Main Menu atuando como "Cancel Ed." durante o Edit Mode
        findViewById<TextView>(R.id.label_main_menu).setOnClickListener {
            if (LoggerManager.isEditing) {
                logger.cancelarEdicao(this)
            }
        }

        findViewById<Button>(R.id.button_about).setOnClickListener {
            navigateToPage(8)
        }
        findViewById<Button>(R.id.button_about_back).setOnClickListener {
            navigateToPage(3)
        }
        findViewById<Button>(R.id.button_Change_User).setOnClickListener {
            navigateToPage(2)
        }
        // --- Botão MEM: cria memória a partir dos campos atuais ---
        findViewById<Button>(R.id.button_logger_mem).setOnClickListener {
            logger.createOrUpdateMemory(this)
        }

        // --- Clique na sugestão: aplica memória aos campos ---
        findViewById<TextView>(R.id.textView_log_memory).setOnClickListener {
            logger.applyMemoryIfNear(this)
        }
        dupeBanner = findViewById(R.id.textView_logger_dupe)
        qsoRecycler = findViewById(R.id.recyclerViewQSOs)

// Banner começa oculto no XML (visibility="gone"); aqui só definimos o clique
        dupeBanner.setOnClickListener {
            // 1) Fechar o teclado e remover foco do campo ativo
            currentFocus?.let { view ->
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
            }

            // 2) Rolar a RecyclerView até o QSO causador (alinhado no topo)
            val targetId = lastDupeQsoId
            if (targetId != null) {
                val adapter = qsoRecycler.adapter
                // Tentamos localizar a posição do item pelo ID no adapter
                val position = when (adapter) {
                    is QsoLogAdapter -> adapter.items.indexOfFirst { it.id.toLong() == targetId }
                    else -> -1
                }

                if (position >= 0) {
                    val lm = qsoRecycler.layoutManager as? LinearLayoutManager
                    if (lm != null) {
                        lm.scrollToPositionWithOffset(position, 0) // item “no topo”
                    } else {
                        qsoRecycler.scrollToPosition(position)
                    }
                } else {
                    // Caso extremo: não achou o item (pode ter sido removido)
                    // Mantemos o comportamento silencioso (sem bloqueio)
                }
            }
        }
        findViewById<Button>(R.id.button_wipe_QSO).setOnClickListener {
            logger.limparCamposQSO(this)
        }
        findViewById<Button>(R.id.button_delete_QSO).setOnClickListener {
            if (!LoggerManager.isEditing) {
                Toast.makeText(this, "Not in Edit Mode.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Confirmação simples
            android.app.AlertDialog.Builder(this)
                .setTitle("Delete QSO")
                .setMessage("Are you sure you want to delete this QSO?")
                .setPositiveButton("Delete") { _, _ ->
                    val ok = logger.deleteQSO(this)
                    if (ok) {
                        hideDupeBanner()
                        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewQSOs)
                        val listaAtualizada = logger.obterQsosDoContestAtual(this)
                        recyclerView.adapter = QsoLogAdapter(listaAtualizada) { item ->
                            logger.entrarEditMode(
                                this, item.id, item.rxCall, item.rxRst,
                                item.rxNr, item.rxExch, item.txRst, item.txExch
                            )
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
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
    // Chame isto quando detectar DUPE
    fun showDupeBannerFor(qsoId: Long?) {
        lastDupeQsoId = qsoId
        dupeBanner.visibility = View.VISIBLE
    }

    // Chame isto quando NÃO houver dupe
    fun hideDupeBanner() {
        lastDupeQsoId = null
        dupeBanner.visibility = View.GONE
    }


}