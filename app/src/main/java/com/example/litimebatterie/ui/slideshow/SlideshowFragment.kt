package com.example.litimebatterie.ui.slideshow

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.litimebatterie.databinding.FragmentSlideshowBinding

class SlideshowFragment : Fragment() {

    private var _binding: FragmentSlideshowBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSlideshowBinding.inflate(inflater, container, false)
        updateGraph()
        return binding.root
    }

    private fun updateGraph() {
        val prefs = requireContext().getSharedPreferences("BatteryPrefs", Context.MODE_PRIVATE)
        val graphBase64 = prefs.getString("graph_bitmap", null)
        val lastUpdate = prefs.getString("last_update", "--:--")

        if (graphBase64 != null) {
            try {
                val decodedString = Base64.decode(graphBase64, Base64.DEFAULT)
                val decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                binding.ivFullGraph.setImageBitmap(decodedByte)
                binding.textSlideshow.text = "Dernière mise à jour : $lastUpdate"
            } catch (e: Exception) {
                binding.textSlideshow.text = "Erreur d'affichage du graphique"
            }
        } else {
            binding.textSlideshow.text = "Aucune donnée disponible. Connectez-vous à la batterie."
        }
    }

    override fun onResume() {
        super.onResume()
        updateGraph()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}