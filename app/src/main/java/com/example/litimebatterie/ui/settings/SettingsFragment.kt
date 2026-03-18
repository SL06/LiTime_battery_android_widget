package com.example.litimebatterie.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.litimebatterie.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        
        val prefs = requireContext().getSharedPreferences("BatteryPrefs", Context.MODE_PRIVATE)
        binding.etEmailDestination.setText(prefs.getString("email_dest", ""))

        binding.btnSaveSettings.setOnClickListener {
            val email = binding.etEmailDestination.text.toString()
            prefs.edit().putString("email_dest", email).apply()
            Toast.makeText(context, "Paramètres sauvegardés", Toast.LENGTH_SHORT).show()
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
