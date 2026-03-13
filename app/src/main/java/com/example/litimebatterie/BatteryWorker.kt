package com.example.litimebatterie

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.util.Base64
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BatteryWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        private const val MAX_HISTORY = 200
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val serviceUuid  = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    private val rxCharUuid   = UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb")
    private val txCharUuid   = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    private val cccdUuid     = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val latch = CountDownLatch(1)
    private val command = byteArrayOf(0x00, 0x00, 0x04, 0x01, 0x13, 0x55, 0xAA.toByte(), 0x17)

    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("BatteryPrefs", Context.MODE_PRIVATE)
        val deviceAddress = prefs.getString("last_device_address", null) ?: return Result.failure()

        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return Result.failure()
        connectToDevice(device)

        val completed = latch.await(30, TimeUnit.SECONDS)
        return if (completed) Result.success() else Result.failure()
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        device.connectGatt(applicationContext, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    gatt.close()
                    latch.countDown()
                    return
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) gatt.discoverServices()
                else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gatt.close()
                    latch.countDown()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val service = gatt.getService(serviceUuid)
                val txChar = service?.getCharacteristic(txCharUuid) ?: return
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

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                val rxChar = gatt.getService(serviceUuid)?.getCharacteristic(rxCharUuid) ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(rxChar, command, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                } else {
                    @Suppress("DEPRECATION")
                    rxChar.value = command
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(rxChar)
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                if (characteristic.uuid == txCharUuid) {
                    parseBatteryData(value)
                    gatt.disconnect()
                }
            }

            @Deprecated("Deprecated in API 33")
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                characteristic.value?.let { onCharacteristicChanged(gatt, characteristic, it) }
            }
        })
    }

    private fun parseBatteryData(data: ByteArray) {
        if (data.size < 66) { latch.countDown(); return }
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val voltage = (buf.getInt(12).toLong() and 0xFFFFFFFFL).toFloat() / 1000f
        val current = buf.getInt(48).toFloat() / 1000f
        val remainingAh = (buf.getShort(62).toInt() and 0xFFFF).toFloat() / 100f
        val capacityAh = (buf.getShort(64).toInt() and 0xFFFF).toFloat() / 100f
        val cellTempC = buf.getShort(52).toInt()
        
        val chargePercent = if (capacityAh > 0f) (remainingAh / capacityAh * 100f).coerceIn(0f, 100f) else 0f
        
        // Color logic for the indicator circle
        val circleColor = getColorForVoltage(voltage)

        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        // --- GESTION DE L'HISTORIQUE ---
        val prefs = applicationContext.getSharedPreferences("BatteryPrefs", Context.MODE_PRIVATE)
        val historyStr = prefs.getString("voltage_history", "") ?: ""
        val historyList = if (historyStr.isEmpty()) mutableListOf() else historyStr.split(",").mapNotNull { it.trim().toFloatOrNull() }.toMutableList()
        
        historyList.add(voltage)
        if (historyList.size > MAX_HISTORY) historyList.removeAt(0)
        
        val newHistoryStr = historyList.joinToString(",")
        
        // --- GENERATION DU GRAPHIQUE ---
        val graphBase64 = generateGraphBase64(historyList)

        prefs.edit().apply {
            putString("level", "%.1f".format(chargePercent))
            putInt("progress", chargePercent.toInt())
            putString("watts", "%.1f W".format(current * voltage))
            putString("temp", "$cellTempC")
            putString("volt_curr", "%.2f V / %.1f A".format(voltage, current))
            putString("remaining_total", "%.2f / %.2f Ah".format(remainingAh, capacityAh))
            putInt("indicator_color", circleColor)
            putString("last_update", currentTime)
            putString("voltage_history", newHistoryStr)
            putString("graph_bitmap", graphBase64)
            apply()
        }

        updateWidget()
        latch.countDown()
    }

    private fun getColorForVoltage(v: Float): Int {
        return when {
            v >= 13.33f -> Color.BLUE
            v >= 13.25f -> Color.GREEN
            v >= 13.20f -> Color.YELLOW
            v >= 13.15f -> "#FFA500".toColorInt() // Orange
            else -> Color.RED
        }
    }

    private fun generateGraphBase64(history: List<Float>): String {
        val width = 400
        val height = 120
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Background
        canvas.drawColor("#222222".toColorInt())
        
        val gridPaintMain = Paint().apply {
            color = Color.DKGRAY
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }

        val gridPaintSecondary = Paint().apply {
            color = "#333333".toColorInt()
            strokeWidth = 1f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
        }

        val textPaint = Paint().apply {
            color = Color.LTGRAY
            textSize = 14f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        
        val minV = 12.5f
        val maxV = 13.5f
        
        // --- LIGNES HORIZONTALES ---
        // 13.0V (Principale)
        val y130 = height - ((13.0f - minV) / (maxV - minV) * height)
        canvas.drawLine(0f, y130, width.toFloat(), y130, gridPaintMain)
        canvas.drawText("13.0V", 5f, y130 - 4f, textPaint)

        // 13.25V et 12.75V (Secondaires)
        val y1325 = height - ((13.25f - minV) / (maxV - minV) * height)
        val y1275 = height - ((12.75f - minV) / (maxV - minV) * height)
        canvas.drawLine(0f, y1325, width.toFloat(), y1325, gridPaintSecondary)
        canvas.drawLine(0f, y1275, width.toFloat(), y1275, gridPaintSecondary)

        // Identification 13.5V et 12.5V (Bords)
        canvas.drawText("13.5V", 5f, 15f, textPaint)
        canvas.drawText("12.5V", 5f, height.toFloat() - 5f, textPaint)

        // --- LIGNES VERTICALES ---
        val gridPaintVertical = Paint().apply {
            color = "#333333".toColorInt()
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        
        val dx = width.toFloat() / (MAX_HISTORY - 1)
        for (i in MAX_HISTORY - 1 downTo 0 step 6) {
            val x = i * dx
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaintVertical)
        }

        // --- TRACÉ DE LA COURBE ---
        val linePaint = Paint().apply {
            strokeWidth = 4f
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        
        if (history.size > 1) {
            val offset = MAX_HISTORY - history.size
            for (i in 0 until history.size - 1) {
                val v1 = history[i].coerceIn(minV, maxV)
                val v2 = history[i+1].coerceIn(minV, maxV)
                
                linePaint.color = getColorForVoltage(v2)
                
                val x1 = (i + offset) * dx
                val y1 = height - ((v1 - minV) / (maxV - minV) * height)
                val x2 = (i + 1 + offset) * dx
                val y2 = height - ((v2 - minV) / (maxV - minV) * height)

                canvas.drawLine(x1, y1, x2, y2, linePaint)
            }
        }
        
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
    }

    private fun updateWidget() {
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val componentName = ComponentName(applicationContext, BatteryWidget::class.java)
        val ids = appWidgetManager.getAppWidgetIds(componentName)
        applicationContext.sendBroadcast(
            android.content.Intent(applicationContext, BatteryWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
        )
    }
}
