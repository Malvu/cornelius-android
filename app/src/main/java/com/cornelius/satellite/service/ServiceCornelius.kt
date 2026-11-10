package com.cornelius.satellite.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.cornelius.satellite.Moteur
import com.cornelius.satellite.Phase
import com.cornelius.satellite.R
import com.cornelius.satellite.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Garde l'écoute en marche même quand l'écran est éteint ou l'application en arrière-plan.
 *
 * Android impose un service au premier plan dès qu'une application enregistre du son
 * en continu : d'où la notification permanente, qui indique aussi l'état.
 */
class ServiceCornelius : Service() {

    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var suivi: Job? = null

    override fun onCreate() {
        super.onCreate()
        creerCanal()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ARRETER) {
            Moteur.obtenir(this).arreter()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this,
            ID_NOTIFICATION,
            notification("Écoute en cours…"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            },
        )
        val moteur = Moteur.obtenir(this)
        moteur.demarrer()
        suivre(moteur)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        suivi?.cancel()
        portee.cancel()
        super.onDestroy()
    }

    private fun suivre(moteur: Moteur) {
        suivi?.cancel()
        suivi = portee.launch {
            moteur.etat.collectLatest { etat ->
                val texte = when {
                    etat.conversation -> "Cornelius te répond"
                    etat.phase == Phase.CONNECTE -> "Veille : dis « Cornelius »"
                    etat.microCoupe -> "Micro coupé"
                    etat.phase == Phase.EN_ATTENTE_ASSOCIATION -> "En attente d'association"
                    else -> etat.message.ifBlank { "Écoute en cours…" }
                }
                val gestionnaire = getSystemService(NotificationManager::class.java)
                gestionnaire?.notify(ID_NOTIFICATION, notification(texte))
            }
        }
    }

    private fun notification(texte: String): Notification {
        val ouvrir = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val arreter = PendingIntent.getService(
            this,
            1,
            Intent(this, ServiceCornelius::class.java).setAction(ACTION_ARRETER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CANAL)
            .setContentTitle("Cornelius")
            .setContentText(texte)
            .setSmallIcon(R.drawable.icone)
            .setOngoing(true)
            .setContentIntent(ouvrir)
            .addAction(0, "Arrêter", arreter)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun creerCanal() {
        val gestionnaire = getSystemService(NotificationManager::class.java) ?: return
        val canal = NotificationChannel(
            CANAL,
            "Écoute de Cornelius",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Affiche que le téléphone écoute « Cornelius »."
            setShowBadge(false)
        }
        gestionnaire.createNotificationChannel(canal)
    }

    companion object {
        private const val CANAL = "cornelius-ecoute"
        private const val ID_NOTIFICATION = 42
        const val ACTION_ARRETER = "com.cornelius.satellite.ARRETER"

        fun demarrer(contexte: Context) {
            val intention = Intent(contexte, ServiceCornelius::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                contexte.startForegroundService(intention)
            } else {
                contexte.startService(intention)
            }
        }

        fun arreter(contexte: Context) {
            contexte.stopService(Intent(contexte, ServiceCornelius::class.java))
        }
    }
}
