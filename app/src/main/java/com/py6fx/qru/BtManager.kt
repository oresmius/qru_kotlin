package com.py6fx.qru

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import android.app.Activity

class BtManager(private val context: Context, private val activity: Activity) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    fun loadPairedDevices(spinner: Spinner) {
        if (bluetoothAdapter == null) {
            Toast.makeText(context, "Bluetooth is not available on this device.", Toast.LENGTH_LONG).show()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(context, "Please enable Bluetooth to search for paired devices.", Toast.LENGTH_LONG).show()
            return
        }

        // Verifica permissões no Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context, "Bluetooth permission is required.", Toast.LENGTH_LONG).show()
                requestBluetoothPermission()
                return
            }
        }

        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices

        if (pairedDevices.isNullOrEmpty()) {
            Toast.makeText(context, "No paired Bluetooth devices found.", Toast.LENGTH_LONG).show()
            return
        }

        // Filtrar apenas dispositivos com nome
        val deviceList = pairedDevices
            .filter { it.name != null }
            .map { it.name }

        if (deviceList.isEmpty()) {
            Toast.makeText(context, "No compatible Bluetooth devices found.", Toast.LENGTH_LONG).show()
            return
        }

        // Preencher o Spinner com os dispositivos encontrados
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, deviceList)
        spinner.adapter = adapter
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
}
