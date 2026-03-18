package com.example.litimebatterie

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.example.litimebatterie.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarMain.toolbar)

        binding.appBarMain.fab?.setOnClickListener {
            sendBatteryDataByEmail()
        }

        val navHostFragment =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment?)!!
        val navController = navHostFragment.navController

        binding.navView?.let { navView ->
            appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.nav_transform, R.id.nav_reflow, R.id.nav_slideshow, R.id.nav_settings
                ),
                binding.drawerLayout
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
            navView.setupWithNavController(navController)
            
            navView.setNavigationItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_clear_data -> {
                        showClearDataDialog()
                        binding.drawerLayout?.closeDrawers()
                        true
                    }
                    else -> {
                        val handled = androidx.navigation.ui.NavigationUI.onNavDestinationSelected(item, navController)
                        if (handled) binding.drawerLayout?.closeDrawers()
                        handled
                    }
                }
            }
        }

        binding.appBarMain.contentMain.bottomNavView?.let {
            appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.nav_transform, R.id.nav_reflow, R.id.nav_slideshow
                )
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
            it.setupWithNavController(navController)
        }
    }

    private fun showClearDataDialog() {
        AlertDialog.Builder(this)
            .setTitle("Effacer les données")
            .setMessage("Voulez-vous vraiment effacer toutes les données sauvegardées (historique et dernier relevé) ?")
            .setPositiveButton("Effacer") { _, _ ->
                val prefs = getSharedPreferences("BatteryPrefs", Context.MODE_PRIVATE)
                val email = prefs.getString("email_dest", "") // On garde l'email
                prefs.edit().clear().apply()
                prefs.edit().putString("email_dest", email).apply() // On remet l'email
                
                Toast.makeText(this, "Données effacées", Toast.LENGTH_SHORT).show()
                
                // Rafraîchir l'écran si on est sur TransformFragment
                val navController = findNavController(R.id.nav_host_fragment_content_main)
                if (navController.currentDestination?.id == R.id.nav_transform) {
                    navController.navigate(R.id.nav_transform)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun sendBatteryDataByEmail() {
        val prefs = getSharedPreferences("BatteryPrefs", Context.MODE_PRIVATE)
        val emailDest = prefs.getString("email_dest", "")
        
        if (emailDest.isNullOrBlank()) {
            Toast.makeText(this, "Veuillez configurer une adresse courriel dans les paramètres", Toast.LENGTH_LONG).show()
            val navController = findNavController(R.id.nav_host_fragment_content_main)
            navController.navigate(R.id.nav_settings)
            return
        }

        val level = prefs.getString("level", "--")
        val watts = prefs.getString("watts", "--")
        val temp = prefs.getString("temp", "--")
        val voltCurr = prefs.getString("volt_curr", "--")
        val cells = prefs.getString("cells_data", "--")
        val lastUpdate = prefs.getString("last_update", "--:--")
        
        val currentTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())

        val subject = "Données Batterie LiTime - $currentTime"
        val body = """
            Rapport d'état de la batterie :
            -------------------------------
            Date du relevé : $currentTime
            Dernière mise à jour (Données) : $lastUpdate
            
            Niveau : $level %
            Puissance : $watts
            Température : $temp °C
            Tension / Courant : $voltCurr
            Tension des cellules : $cells
            
            Envoyé depuis l'application LiTime Batterie.
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(emailDest))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }

        try {
            startActivity(Intent.createChooser(intent, "Envoyer le courriel via..."))
        } catch (e: Exception) {
            Toast.makeText(this, "Aucune application de courriel trouvée", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val result = super.onCreateOptionsMenu(menu)
        val navView: NavigationView? = findViewById(R.id.nav_view)
        if (navView == null) {
            menuInflater.inflate(R.menu.overflow, menu)
        }
        return result
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_settings -> {
                val navController = findNavController(R.id.nav_host_fragment_content_main)
                navController.navigate(R.id.nav_settings)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
