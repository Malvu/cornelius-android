package com.cornelius.satellite.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.cornelius.satellite.Moteur
import com.cornelius.satellite.service.ServiceCornelius

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val moteur = Moteur.obtenir(this)

        setContent {
            ThemeCornelius {
                val demandeur = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { accords ->
                    if (accords[Manifest.permission.RECORD_AUDIO] != false) {
                        ServiceCornelius.demarrer(this)
                    }
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    EcranCornelius(
                        moteur = moteur,
                        demarrer = {
                            val manquantes = permissionsManquantes()
                            if (manquantes.isEmpty()) {
                                ServiceCornelius.demarrer(this)
                            } else {
                                demandeur.launch(manquantes.toTypedArray())
                            }
                        },
                        arreter = {
                            moteur.arreter()
                            ServiceCornelius.arreter(this)
                        },
                    )
                }
            }
        }
    }

    /** Permissions nécessaires : le micro, et les notifications pour la pastille du service. */
    private fun permissionsManquantes(): List<String> {
        val liste = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            liste.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            liste.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return liste
    }
}
