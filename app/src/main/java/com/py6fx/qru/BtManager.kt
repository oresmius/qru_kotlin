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
import android.view.View
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BtManager(private val context: Context, private val activity: Activity, private val deviceContainer: LinearLayout) {

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // UUID padrão para SPP
    private var selectedDevice: BluetoothDevice? = null

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

        // Limpar o layout antes de adicionar os dispositivos
        deviceContainer.removeAllViews()

        // Criar um TextView para cada dispositivo pareado
        for (device in pairedDevices) {
            val deviceTextView = TextView(context)
            deviceTextView.text = "${device.name} - ${device.address}"
            deviceTextView.textSize = 18f
            deviceTextView.setPadding(16, 16, 16, 16)
            deviceTextView.setBackgroundColor(Color.TRANSPARENT) // Cor padrão

            deviceTextView.setOnClickListener {
                // Resetar a cor de todos os dispositivos antes de selecionar um novo
                for (i in 0 until deviceContainer.childCount) {
                    (deviceContainer.getChildAt(i) as TextView).setBackgroundColor(Color.TRANSPARENT)
                }

                // Definir a nova seleção e destacar o item
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
            val socket: BluetoothSocket = selectedDevice!!.createRfcommSocketToServiceRecord(uuid)
            socket.connect()
            Log.d("BluetoothTest", "✅ Conexão Bluetooth estabelecida com ${selectedDevice!!.name}!")
            showToast(" Connected to ${selectedDevice!!.name}!")

            val outputStream: OutputStream = socket.outputStream
            val inputStream: InputStream = socket.inputStream


            val comando = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x03)
            val comandoHex = comando.joinToString(" ") { String.format("%02X", it) }
            Log.d("BluetoothTest", "📨 Enviando CAT: $comandoHex")
            outputStream.write(comando) // Enviar o comando
            outputStream.flush()
            Thread.sleep(100)
            Log.d("BluetoothTest", "📨 Comando CAT enviado ")

            // 🔍 Ler resposta byte a byte com timeout de 3 segundos
            val resposta = ByteArray(5)
            var bytesLidos = 0
            val tempoLimite = System.currentTimeMillis() + 5000

            while (bytesLidos < 5 && System.currentTimeMillis() < tempoLimite) {
                if (inputStream.available() > 0) {
                    resposta[bytesLidos] = inputStream.read().toByte()
                    bytesLidos++
                }
            }

            // Exibir resposta ou erro
            if (bytesLidos < 5) {
                Log.w("BluetoothTest", "⚠️ Resposta incompleta. Bytes recebidos: $bytesLidos")
                showToast("Error: Incomplete response. Try again.")
                return
            }

            // ✅ Formata corretamente a frequência
            val formattedFreq = interpretarFrequencia(resposta)
            Log.d("BluetoothTest", "📡 Resposta do rádio: $formattedFreq")
            showToast("Freq: $formattedFreq MHz")


            // Fecha a conexão após o teste
            //socket.close()
            //Log.d("BluetoothTest", "🔴 Conexão encerrada com ")
            //showToast("Connection closed")

        } catch (e: IOException) {
            Log.e("BluetoothTest", "❌ Erro ao conectar: ${e.message}")
            showToast("Error connecting: ${e.message}")
        }

    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun interpretarFrequencia(resposta: ByteArray): String {
        if (resposta.size != 5) return "Erro na resposta"

        // Extrai os valores BCD corretamente
        val freqBcd = resposta.joinToString("") { "%02X".format(it) }

        // Reorganiza os blocos para corresponder ao LCD do rádio
        val formattedFreq = "${freqBcd.substring(0, 2)}.${freqBcd.substring(2, 5)}.${freqBcd.substring(5)}"

        return formattedFreq
    }


}