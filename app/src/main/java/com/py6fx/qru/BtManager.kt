package com.py6fx.qru

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import android.app.Activity
import android.bluetooth.BluetoothSocket
import android.graphics.Color
import android.util.Log
import java.util.UUID

class BtManager(
    private val context: Context,
    private val activity: Activity,
    private val deviceContainer: LinearLayout,
    private val onQrgUpdate: ((String) -> Unit)? = null,
    private val onModeUpdate: ((String) -> Unit)? = null
) {

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // UUID padrão para SPP
    private var selectedDevice: BluetoothDevice? = null
    private var bluetoothSocket: BluetoothSocket? = null  // Armazena o socket para manter a conexão
    private var pollingThread: Thread? = null
    private var pollingActive: Boolean = false

    fun loadPairedDevices() {
        if (bluetoothAdapter == null) {
            showToast("Bluetooth is not available on this device.")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            showToast("Please enable Bluetooth.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermission()
            return
        }

        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices

        if (pairedDevices.isNullOrEmpty()) {
            showToast("No paired Bluetooth devices found.")
            return
        }

        deviceContainer.removeAllViews()

        for (device in pairedDevices) {
            val deviceTextView = TextView(context)
            deviceTextView.text = "${device.name} - ${device.address}"
            deviceTextView.textSize = 18f
            deviceTextView.setPadding(16, 16, 16, 16)
            deviceTextView.setBackgroundColor(Color.TRANSPARENT)

            deviceTextView.setOnClickListener {
                for (i in 0 until deviceContainer.childCount) {
                    (deviceContainer.getChildAt(i) as TextView).setBackgroundColor(Color.TRANSPARENT)
                }
                selectedDevice = device
                deviceTextView.setBackgroundColor(Color.parseColor("#6200EE"))
                showToast("Selected: ${device.name}")
                Log.d("BluetoothTest", "📡 Dispositivo selecionado: ${device.name} (${device.address})")
            }

            deviceContainer.addView(deviceTextView)
        }
    }

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_BLUETOOTH_PERMISSION
            )
        }
    }

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSION = 1
    }

    fun connectToDevice() {
        if (selectedDevice == null) {
            showToast("No devices selected")
            Log.e("BluetoothTest", "❌ Nenhum dispositivo selecionado")
            return
        }

        Log.d("BluetoothTest", "🔵 Tentando conectar a ${selectedDevice!!.name} (${selectedDevice!!.address})...")

        try {
            // Se já houver um socket conectado, evita nova conexão
            if (bluetoothSocket?.isConnected == true) {
                showToast("Already connected to ${selectedDevice!!.name}")
                return
            }

            bluetoothSocket = selectedDevice!!.createRfcommSocketToServiceRecord(uuid)
            bluetoothSocket!!.connect()
            Log.d("BluetoothTest", "✅ Conexão Bluetooth estabelecida com ${selectedDevice!!.name}!")
            showToast("Connected to ${selectedDevice!!.name}!")

            // Inicia o polling automático, se ainda não estiver rodando
            if (!pollingActive) {
                pollingActive = true
                pollingThread = Thread {
                    try {
                        val outputStream = bluetoothSocket!!.outputStream
                        val inputStream = bluetoothSocket!!.inputStream
                        while (pollingActive) {
                            val comando = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x03)
                            outputStream.write(comando)
                            val resposta = ByteArray(5)
                            var bytesLidos = 0
                            val timeout = System.currentTimeMillis() + 2000
                            while (bytesLidos < 5 && System.currentTimeMillis() < timeout) {
                                if (inputStream.available() > 0) {
                                    resposta[bytesLidos] = inputStream.read().toByte()
                                    bytesLidos++
                                }
                            }
                            if (bytesLidos == 5) {
                                val qrg = interpretarFrequencia(resposta) // usa resposta[0..3]
                                val modo = interpretarModo(resposta)      // usa resposta[4]
                                onQrgUpdate?.let { callback ->
                                    activity.runOnUiThread { callback(qrg) }
                                }
                                onModeUpdate?.let { callback ->
                                    activity.runOnUiThread { callback(modo) }
                                }
                            }
                            Thread.sleep(1000)
                        }

                    } catch (e: Exception) {
                        Log.e("BluetoothTest", "Erro no polling: ${e.message}")
                    }
                }.also { it.start() }
            }
        } catch (e: Exception) {
            Log.e("BluetoothTest", "Erro ao conectar: ${e.message}")
            showToast("Erro ao conectar: ${e.message}")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun interpretarFrequencia(resposta: ByteArray): String {
        if (resposta.size != 5) return "Erro na resposta"

        // Extrai os valores BCD corretamente
        val freqBcd = resposta.joinToString("") { "%02X".format(it) }

        // Corrige a posição dos pontos decimais
        var formattedFreq = "${freqBcd.substring(0, 2)}${freqBcd.substring(2, 3)}.${freqBcd.substring(3, 6)}.${freqBcd.substring(6, 8)}"

        // Remover zero à esquerda se for menor que 10 MHz
        if (formattedFreq.startsWith("0")) {
            formattedFreq = formattedFreq.substring(1) // Remove o primeiro caractere se for "0"
        }

        return formattedFreq
    }

    fun interpretarModo(resposta: ByteArray): String {
        if (resposta.size != 5) return "Unknown"
        val modoByte = resposta[4].toInt() and 0xFF
        return when (modoByte) {
            0x00 -> "LSB"
            0x01 -> "USB"
            0x02 -> "CW"
            0x03 -> "CWR"
            0x04 -> "AM"
            0x08 -> "FM"
            0x0A -> "DIG"
            0x0C -> "PKT"
            else -> "Unknown"
        }
    }
}
