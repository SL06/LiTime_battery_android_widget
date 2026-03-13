package com.example.litimebatterie

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import android.widget.RemoteViews
import androidx.work.*
import java.util.concurrent.TimeUnit

class BatteryWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        scheduleBatteryUpdate(context)
    }

    override fun onEnabled(context: Context) {
        scheduleBatteryUpdate(context)
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.battery_widget)
            val prefs = context.getSharedPreferences("BatteryPrefs", Context.MODE_PRIVATE)
            
            views.setTextViewText(R.id.widget_level, "Level: ${prefs.getString("level", "--")}%")
            views.setTextViewText(R.id.widget_watts, "Watts: ${prefs.getString("watts", "--")}")
            views.setTextViewText(R.id.widget_temp, "Temp: ${prefs.getString("temp", "--")}°C")
            views.setTextViewText(R.id.widget_volt, "V/A: ${prefs.getString("volt_curr", "--")}")
            views.setTextViewText(R.id.widget_capacity, "Cap: ${prefs.getString("remaining_total", "--")}")
            views.setTextViewText(R.id.widget_last_update, "Last update: ${prefs.getString("last_update", "--:--")}")
            
            val color = prefs.getInt("indicator_color", Color.GRAY)
            views.setInt(R.id.widget_indicator, "setColorFilter", color)
            
            val progress = prefs.getInt("progress", 0)
            views.setProgressBar(R.id.widget_progress, 100, progress, false)

            val graphBase64 = prefs.getString("graph_bitmap", null)
            if (graphBase64 != null) {
                try {
                    val decodedString = Base64.decode(graphBase64, Base64.DEFAULT)
                    val decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                    views.setImageViewBitmap(R.id.widget_graph, decodedByte)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun scheduleBatteryUpdate(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<BatteryWorker>(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "BatteryUpdateWork",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
