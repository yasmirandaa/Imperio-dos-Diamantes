
package com.ifpr.androidapptemplatear

import android.os.Bundle
import android.media.MediaPlayer
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.ifpr.androidapptemplatear.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var musica: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val navController =
            findNavController(R.id.nav_host_fragment_activity_main)

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_camera,
                R.id.navigation_changeobject
            )
        )

        navView.setupWithNavController(navController)

        // Iniciar música de fundo
        musica = MediaPlayer.create(this, R.raw.som1)
        musica?.isLooping = true
        musica?.start()
    }

    override fun onDestroy() {
        musica?.release()
        musica = null
        super.onDestroy()
    }
}
