package com.example.litimebatterie.ui.transform

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.litimebatterie.databinding.FragmentTransformBinding
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class TransformFragment : Fragment() {

    private var _binding: FragmentTransformBinding? = null
    private val binding get() = _binding!!

    private var bluetoothGatt: BluetoothGatt? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    
    private val serviceUuid = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val rxCharUuid  = UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb")
    private val txCharUuid  = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val cccdUuid    = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())
    private val queryCommand = byteArrayOf(0x00, 0x00, 0x04, 0x01, 0x13, 0x55, 0xAA.toByte(), 0x17)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) startBluetoothScan()
        else setStatus("Erreur : Permissions Bluetooth/Position manquantes.")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTransformBinding.inflate(inflater, container, false)
        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        
        binding.btnConnect.setOnClickListener { 
            if (isScanning) stopScan() else startBluetoothScan()
        }

        return binding.root
    }

    private fun setStatus(msg: String) {
        handler.post {
            binding.etCells.setText(msg)
        }
    }

    private fun startBluetoothScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            setStatus("Bluetooth désactivé")
            return
        }

        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissions.any { ActivityCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissionLauncher.launch(permissions)
            return
        }

        scanLeDevice()
    }

    @SuppressLint("MissingPermission")
    private fun scanLeDevice() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        isScanning = true
        binding.btnConnect.text = "ARRÊTER LA RECHERCHE"
        setStatus("Recherche en cours...")

        handler.postDelayed({ if (isScanning) stopScan() }, 15000)
        scanner.startScan(null, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        isScanning = false
        binding.btnConnect.text = "RECHERCHER BATTERIE"
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            @SuppressLint("MissingPermission")
            val name = result.scanRecord?.deviceName ?: device.name ?: ""
            if (name.contains("LiTime", true) || name.contains("BNNA70", true)) {
                setStatus("Batterie trouvée : $name")
                stopScan()
                connectToDevice(device)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        setStatus("Connexion à ${device.address}...")
        bluetoothGatt = device.connectGatt(requireContext().applicationContext, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                setStatus("Connecté. Lecture des données...")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                setStatus("Déconnecté.")
                bluetoothGatt?.close()
                bluetoothGatt = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(serviceUuid)
                val txChar = service?.getCharacteristic(txCharUuid)
                
                if (txChar != null) {
                    gatt.setCharacteristicNotification(txChar, true)
                    val descriptor = txChar.getDescriptor(cccdUuid)
                    if (descriptor != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(descriptor)
                        }
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && descriptor.uuid == cccdUuid) {
                val rxChar = gatt.getService(serviceUuid)?.getCharacteristic(rxCharUuid)
                if (rxChar != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeCharacteristic(rxChar, queryCommand, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                    } else {
                        @Suppress("DEPRECATION")
                        rxChar.value = queryCommand
                        @Suppress("DEPRECATION")
                        gatt.writeCharacteristic(rxChar)
                    }
                }
            }
        }

        @Deprecated("Deprecated in API 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            characteristic.value?.let { onCharacteristicChanged(gatt, characteristic, it) }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == txCharUuid && value.size >= 66) {
                parseBatteryData(value)
            }
        }
    }

    private fun parseBatteryData(data: ByteArray) {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        try {
            val voltage = (buffer.getInt(12).toLong() and 0xFFFFFFFFL) / 1000.0
            val current = buffer.getInt(48) / 1000.0
            val remainingAh = (buffer.getShort(62).toInt() and 0xFFFF) / 100.0
            val capacityAh = (buffer.getShort(64).toInt() and 0xFFFF) / 100.0
            val tempC = buffer.getShort(52).toInt()
            
            val cellVolts = mutableListOf<Double>()
            for (i in 0 until 16) {
                val cv = (buffer.getShort(16 + i * 2).toInt() and 0xFFFF)
                if (cv != 0) cellVolts.add(cv / 1000.0)
            }

            val percent = if (capacityAh > 0) (remainingAh / capacityAh * 100).coerceAtMost(100.0) else 0.0
            
            handler.post {
                binding.etWatts.setText("%.1f W".format(current * voltage))
                binding.pbBattery.progress = percent.toInt()
                binding.etTemp.setText("$tempC °C")
                binding.etVoltCurr.setText("%.2f V / %.1f A".format(voltage, current))
                binding.etCells.setText(cellVolts.joinToString(" | "))
            }
        } catch (e: Exception) { 
            Log.e("BLE", "Erreur parsing : ${e.message}") 
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopScan()
        try {
            @SuppressLint("MissingPermission")
            bluetoothGatt?.close()
        } catch (e: Exception) {}
        _binding = null
    }
}
