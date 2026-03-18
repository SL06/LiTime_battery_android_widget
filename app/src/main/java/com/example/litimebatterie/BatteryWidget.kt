package com.example.litimebatterie

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
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
            
            views.setTextViewText(R.id.widget_level, "Niveau: ${prefs.getString("level", "--")}%")
            views.setTextViewText(R.id.widget_watts, "Watts: ${prefs.getString("watts", "--")}")
            views.setTextViewText(R.id.widget_temp, "Temp: ${prefs.getString("temp", "--")}°C")
            views.setTextViewText(R.id.widget_volt, "V/A: ${prefs.getString("volt_curr", "--")}")
            views.setTextViewText(R.id.widget_capacity, "Cap: ${prefs.getString("remaining_total", "--")}")
            views.setTextViewText(R.id.widget_last_update, "MàJ: ${prefs.getString("last_update", "--:--")}")
            
            val color = prefs.getInt("indicator_color", Color.GRAY)
            views.setInt(R.id.widget_indicator, "setColorFilter", color)
            
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

            // Action pour ouvrir l'application lors d'un clic sur le widget
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.data_container, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_graph, pendingIntent)

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
