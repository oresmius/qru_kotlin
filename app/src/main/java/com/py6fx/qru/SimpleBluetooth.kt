package com.py6fx.qru

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import android.widget.Toast
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class SimpleBluetooth(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val deviceMacAddress = "98:DA:20:07:19:CC" // ⚠️ SUBSTITUA pelo MAC real do FT-817ND!
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // UUID padrão para SPP

    fun connectToDevice() {
        Log.d("BluetoothTest", "🔵 Iniciando conexão Bluetooth...")

        if (bluetoothAdapter == null) {
            showToast("Erro: Este dispositivo não suporta Bluetooth!")
            Log.e("BluetoothTest", "❌ Bluetooth não suportado")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            showToast("Bluetooth está desligado! Ative manualmente.")
            Log.w("BluetoothTest", "⚠️ Bluetooth desligado")
            return
        }

        Log.d("BluetoothTest", "🔍 Procurando dispositivo pareado com MAC: $deviceMacAddress")
        val device: BluetoothDevice? = bluetoothAdapter.bondedDevices.find { it.address == deviceMacAddress }

        if (device == null) {
            showToast("Dispositivo não encontrado! Certifique-se de que está pareado.")
            Log.e("BluetoothTest", "❌ Dispositivo com MAC $deviceMacAddress não encontrado")
            return
        }

        Log.d("BluetoothTest", "📡 Dispositivo encontrado: ${device.name} (${device.address}). Tentando conectar...")

        try {
            val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            socket.connect()
            Log.d("BluetoothTest", "✅ Conexão Bluetooth estabelecida com ${device.name}!")
            showToast("Conectado a ${device.name}!")

            // Enviar comando CAT para solicitar frequência
            val outputStream: OutputStream = socket.outputStream
            val inputStream: InputStream = socket.inputStream

            val comando = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x03)
            val comandoHex = comando.joinToString(" ") { String.format("%02X", it) }
            Log.d("BluetoothTest", "📨 Enviando CAT: $comandoHex")
            outputStream.write(comando)
            Log.d("BluetoothTest", "📨 Comando CAT enviado ")

            // Aguarda a resposta de até 5 bytes do rádio
            val resposta = ByteArray(5)
            var bytesLidos = 0
            val tempoLimite = System.currentTimeMillis() + 3000 // Espera até 3 segundos

            while (bytesLidos < 5 && System.currentTimeMillis() < tempoLimite) {
                if (inputStream.available() > 0) {
                    resposta[bytesLidos] = inputStream.read().toByte()
                    bytesLidos++
                }
            }

            // Exibir resposta ou erro
            if (bytesLidos == 5) {
                val respostaFormatada = resposta.joinToString(" ") { String.format("%02X", it) }
                Log.d("BluetoothTest", "📡 Resposta do rádio: $respostaFormatada")
                showToast("Freq: $respostaFormatada")
            } else {
                Log.w("BluetoothTest", "⚠️ Resposta incompleta. Bytes recebidos: $bytesLidos")
                showToast("Erro: resposta incompleta")
            }

            // Fecha a conexão após o teste
            //socket.close()
            Log.d("BluetoothTest", "🔴 Conexão encerrada com ${device.name}.")
            showToast("Conexão encerrada.")

        } catch (e: IOException) {
            Log.e("BluetoothTest", "❌ Erro ao conectar: ${e.message}")
            showToast("Erro ao conectar: ${e.message}")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
