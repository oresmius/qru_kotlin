package com.py6fx.qru

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast

class BtManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    fun loadPairedDevices(spinner: Spinner) {
        if (bluetoothAdapter == null) {
            Toast.makeText(context, "Bluetooth não disponível neste dispositivo.", Toast.LENGTH_LONG).show()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(context, "Ative o Bluetooth para buscar dispositivos pareados.", Toast.LENGTH_LONG).show()
            return
        }

        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices

        if (pairedDevices.isNullOrEmpty()) {
            Toast.makeText(context, "Nenhum dispositivo Bluetooth pareado encontrado.", Toast.LENGTH_LONG).show()
            return
        }

        // Filtrar apenas dispositivos compatíveis (SPP - Serial Port Profile)
        val deviceList = pairedDevices
            .filter { it.name != null } // Evita dispositivos sem nome
            .map { it.name } // Obtém apenas os nomes dos dispositivos

        if (deviceList.isEmpty()) {
            Toast.makeText(context, "Nenhum dispositivo compatível encontrado.", Toast.LENGTH_LONG).show()
            return
        }

        // Preencher o Spinner com os dispositivos encontrados
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, deviceList)
        spinner.adapter = adapter
    }
}

