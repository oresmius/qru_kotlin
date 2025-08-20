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


class MainActivity : AppCompatActivity() {

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
                findViewById<TextView>(R.id.qrg_indicator).text = qrg
                //atualiza sugestão de memória quando a QRG muda
                updateMemorySuggestionForCurrentFreq()
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
                    memories.clear()
                    findViewById<TextView>(R.id.textView_log_memory).text = ""
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
                    memories.clear()
                    findViewById<TextView>(R.id.textView_log_memory).text = ""
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
                    findViewById<TextView>(R.id.qrg_indicator).text = qrg
                    updateMemorySuggestionForCurrentFreq()
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
            findViewById<ViewFlipper>(R.id.viewFlipper).post {
                // --- Seu código antigo
                LoggerManager().preencherTXExch(this)
                val modo = findViewById<TextView>(R.id.mode_indicator).text.toString().trim()
                val editTextTX = findViewById<EditText>(R.id.editText_TX_RST)
                val editTextRX = findViewById<EditText>(R.id.editText_RX_RST)
                LoggerManager().RSTAutomatico(modo, editTextTX, editTextRX)

                val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewQSOs)
                recyclerView.layoutManager = LinearLayoutManager(this)

                // Busca os QSOs
                val listaQsos = LoggerManager().obterQsosDoContestAtual(this)

                val adapter = QsoLogAdapter(
                    listaQsos
                ) { item ->
                    // Long-press → Edit Mode
                    LoggerManager().entrarEditMode(
                        activity = this,
                        qsoId = item.id,              // requer que a lista traga o id
                        rxCall = item.rxCall,
                        rxRst = item.rxRst,
                        rxNr = item.rxNr,
                        rxExch = item.rxExch,
                        txRst = item.txRst,
                        txExch = item.txExch
                    )
                }
                recyclerView.adapter = adapter
                updateMemorySuggestionForCurrentFreq()
            }
        }

        findViewById<Button>(R.id.button_resume_contest).setOnClickListener {
            val contestIndicator = findViewById<TextView>(R.id.contest_indicator)
            val before = contestIndicator.text.toString()

            contestManager.resumeContest(findViewById(R.id.pag_6))

            contestIndicator.post {
                val after = contestIndicator.text.toString()
                if (after.isNotEmpty() && after != before) {
                    memories.clear()
                    findViewById<TextView>(R.id.textView_log_memory).text = ""
                }
            }
        }

        findViewById<Button>(R.id.button_edit_contest).setOnClickListener {
            contestManager.editContest(findViewById(R.id.pag_6))
        }

        //chamada função de log
        findViewById<Button>(R.id.button_log_QSO).setOnClickListener {
            if (LoggerManager.isEditing) {
                val ok = LoggerManager().atualizarQSO(this)
                if (ok) {
                    // Recarrega a lista imediatamente e volta ao fluxo normal (Log Mode)
                    val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewQSOs)
                    val listaAtualizada = LoggerManager().obterQsosDoContestAtual(this)
                    val adapter = QsoLogAdapter(listaAtualizada) { item ->
                        LoggerManager().entrarEditMode(
                            this, item.id, item.rxCall, item.rxRst, item.rxNr,
                            item.rxExch, item.txRst, item.txExch
                        )
                    }
                    recyclerView.adapter = adapter
                }
            } else {
                // Comportamento já existente de Log Mode
                LoggerManager().logQSO(this)
                LoggerManager().limparCamposQSO(this)
                LoggerManager().preencherTXExch(this)



                val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewQSOs)
                val listaAtualizada = LoggerManager().obterQsosDoContestAtual(this)
                val adapter = QsoLogAdapter(listaAtualizada) { item ->
                    LoggerManager().entrarEditMode(
                        this, item.id, item.rxCall, item.rxRst, item.rxNr,
                        item.rxExch, item.txRst, item.txExch
                    )
                }
                recyclerView.adapter = adapter
            }
        }
        findViewById<Button>(R.id.button_wipe_QSO).setOnClickListener {
            LoggerManager().limparCamposQSO(this)
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
                // Cancelar edição: ignora mudanças e volta ao Log Mode
                LoggerManager().cancelarEdicao(this)
            } else {
                // Função primária: sair do logger para o Main Menu
                navigateToPage(3)
            }

        }
        // Label do Main Menu atuando como "Cancel Ed." durante o Edit Mode
        findViewById<TextView>(R.id.label_main_menu).setOnClickListener {
            if (LoggerManager.isEditing) {
                LoggerManager().cancelarEdicao(this)
            } else {
                // opcional: manter só como label fora do Edit Mode, ou navegar:
                // navigateToPage(3)
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
        // --- Botão MEM: cria/atualiza memória a partir dos campos atuais ---
        findViewById<Button>(R.id.button_logger_mem).setOnClickListener {
            // 1) Valida Rx Call
            val rxCall = findViewById<EditText>(R.id.editText_RX_Call).text.toString().trim().uppercase()
            if (rxCall.isEmpty()) {
                // Sem Toasts/avisos; apenas não faz nada se vazio.
                return@setOnClickListener


            }

            // 2) Captura QRG atual
            val qrgStr = findViewById<TextView>(R.id.qrg_indicator).text.toString().trim()
            val freqKHz = qrgStringToKHz(qrgStr) ?: return@setOnClickListener

            // 3) Coleta demais campos
            val rxRst  = findViewById<EditText>(R.id.editText_RX_RST).text.toString().trim().ifEmpty { null }
            val txRst  = findViewById<EditText>(R.id.editText_TX_RST).text.toString().trim().ifEmpty { null }
            val rxNr   = findViewById<EditText>(R.id.editText_RX_Nr).text.toString().trim().ifEmpty { null }
            val rxExch = findViewById<EditText>(R.id.editText_RX_Exch).text.toString().trim().ifEmpty { null }
            val txExch = findViewById<EditText>(R.id.editText_TX_Exch).text.toString().trim().ifEmpty { null }

            // 4) Upsert na janela ±2.5 kHz
            val m = MemoryQSO(freqKHz, rxCall, rxRst, txRst, rxNr, rxExch, txExch)
            upsertMemoryAt(freqKHz, m)

            // 5) Sinal: escrever o RX Call no textView_log_memory
            findViewById<TextView>(R.id.textView_log_memory).text = rxCall
        }

            // --- Clique na sugestão: aplica memória aos campos ---
        findViewById<TextView>(R.id.textView_log_memory).setOnClickListener {
            val shown = findViewById<TextView>(R.id.textView_log_memory).text.toString().trim()
            if (shown.isEmpty()) return@setOnClickListener
            val qrgStr = findViewById<TextView>(R.id.qrg_indicator).text.toString().trim()
            val freqKHz = qrgStringToKHz(qrgStr) ?: return@setOnClickListener

            val mem = findMemoryNear(freqKHz) ?: return@setOnClickListener

            // Preenche TODOS os campos definidos; ausentes → limpa
            findViewById<EditText>(R.id.editText_RX_Call).setText(mem.rxCall)
            findViewById<EditText>(R.id.editText_RX_RST).setText(mem.rxRst ?: "")
            findViewById<EditText>(R.id.editText_TX_RST).setText(mem.txRst ?: "")
            findViewById<EditText>(R.id.editText_RX_Nr).setText(mem.rxNr ?: "")
            findViewById<EditText>(R.id.editText_RX_Exch).setText(mem.rxExch ?: "")
            findViewById<EditText>(R.id.editText_TX_Exch).setText(mem.txExch ?: "")

            // A memória permanece viva. Nada de toasts/avisos.
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
    // --- Memórias de pré-log (voláteis, sessão atual) ---
    private data class MemoryQSO(
        val freqKHz: Double,
        val rxCall: String,
        val rxRst: String?,
        val txRst: String?,
        val rxNr: String?,
        val rxExch: String?,
        val txExch: String?
    )

    private val memories = mutableListOf<MemoryQSO>()
    private val MEM_TOL_KHZ = 2.5

    // Converte "7.074.00" -> 7074.00 kHz
    private fun qrgStringToKHz(qrgStr: String): Double? {
        val num = qrgStr.replace(".", "").toDoubleOrNull() ?: return null
        return num / 100.0
    }

    // Encontra memória na janela ±2.5 kHz
    private fun findMemoryNear(freqKHz: Double): MemoryQSO? =
        memories.minByOrNull { kotlin.math.abs(it.freqKHz - freqKHz) }
            ?.takeIf { kotlin.math.abs(it.freqKHz - freqKHz) <= MEM_TOL_KHZ }

    // Cria/substitui memória na janela
    private fun upsertMemoryAt(freqKHz: Double, m: MemoryQSO) {
        val idx = memories.indexOfFirst { kotlin.math.abs(it.freqKHz - freqKHz) <= MEM_TOL_KHZ }
        if (idx >= 0) memories[idx] = m else memories.add(m)
    }

    // Atualiza a sugestão visual (mostra só na página do logger, index 7)
    private fun updateMemorySuggestionForCurrentFreq() {
        if (viewFlipper.displayedChild != 7) {
            findViewById<TextView>(R.id.textView_log_memory).text = ""
            return
        }
        val qrgStr = findViewById<TextView>(R.id.qrg_indicator).text.toString().trim()
        val freqKHz = qrgStringToKHz(qrgStr)
        val tv = findViewById<TextView>(R.id.textView_log_memory)
        if (freqKHz == null) { tv.text = ""; return }

        val mem = findMemoryNear(freqKHz)
        tv.text = mem?.rxCall ?: ""
    }
}