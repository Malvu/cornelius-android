package com.cornelius.satellite

import android.content.Context
import android.util.Log
import com.cornelius.satellite.audio.HautParleur
import com.cornelius.satellite.audio.Microphone
import com.cornelius.satellite.data.ModeEcoute
import com.cornelius.satellite.data.Reglages
import com.cornelius.satellite.net.Appairage
import com.cornelius.satellite.net.ConfianceCornelius
import com.cornelius.satellite.net.Protocole
import com.cornelius.satellite.net.Satellite
import com.cornelius.satellite.wake.DetecteurMotAppel
import com.cornelius.satellite.wake.WakePipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Le cœur de l'application : elle fait du téléphone un satellite de Cornelius.
 *
 * Trois choses tournent en même temps :
 *   - le micro, lu par blocs de 80 ms ;
 *   - le calcul local du mot d'appel, sur son propre fil (il ne doit jamais retarder le son) ;
 *   - la connexion à Cornelius, qui porte l'état et le son de la conversation.
 *
 * En mode **direct**, le son du micro part en continu, comme sur un satellite ESP32 :
 * c'est Cornelius qui cherche « Cornelius ».
 * En mode **économie**, le téléphone cherche lui-même le mot d'appel et n'envoie du son
 * qu'après l'avoir entendu : moins de réseau, moins de batterie.
 */
class Moteur private constructor(private val contexte: Context) {

    companion object {
        private const val TAG = "Cornelius"

        /** Deux secondes de son gardées avant une détection, pour ne pas couper le début de la phrase. */
        private const val PREROLL_MAX = 25

        /** Si Cornelius n'ouvre pas la conversation après une détection locale, on revient en veille. */
        private const val ATTENTE_OUVERTURE = 6_000L

        @Volatile
        private var instance: Moteur? = null

        fun obtenir(contexte: Context): Moteur =
            instance ?: synchronized(this) {
                instance ?: Moteur(contexte.applicationContext).also { instance = it }
            }
    }

    val reglages = Reglages(contexte)

    private val _etat = MutableStateFlow(Etat(microCoupe = reglages.microCoupe, nom = reglages.nom))
    val etat: StateFlow<Etat> = _etat.asStateFlow()

    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val filMicro = Executors.newSingleThreadExecutor { Thread(it, "cornelius-micro").apply { isDaemon = true } }
        .asCoroutineDispatcher()
    private val filMot = Executors.newSingleThreadExecutor { Thread(it, "cornelius-mot").apply { isDaemon = true } }
        .asCoroutineDispatcher()
    private val filSon = Executors.newSingleThreadExecutor { Thread(it, "cornelius-son").apply { isDaemon = true } }
        .asCoroutineDispatcher()

    private val micro = Microphone()
    private val hautParleur = HautParleur()

    private var satellite: Satellite? = null
    private var appairage: Appairage? = null
    private var detecteur: DetecteurMotAppel? = null
    private var canalSons: Channel<ShortArray>? = null
    private var canalMot: Channel<ShortArray>? = null

    @Volatile private var actif = false
    @Volatile private var ecouteServeur = false
    @Volatile private var ouvertLocal = false
    @Volatile private var annihilationEcho = false

    private val preroll = ArrayDeque<ShortArray>()
    /** Son d'avant la détection, à envoyer en premier par le fil du micro. */
    @Volatile private var aRejouer: List<ShortArray>? = null
    private var travailMicro: Job? = null
    private var travailReconnexion: Job? = null
    private var essais = 0

    // ---------------------------------------------------------------- marche / arrêt

    /** Démarre l'écoute. Le micro doit déjà être autorisé. */
    fun demarrer() {
        if (actif) return
        actif = true
        essais = 0
        if (reglages.jeton.isEmpty()) {
            associer()
            return
        }
        demarrerAudio()
        connecter()
    }

    fun arreter() {
        actif = false
        travailReconnexion?.cancel()
        travailReconnexion = null
        satellite?.fermer()
        satellite = null
        appairage?.fermer()
        appairage = null
        travailMicro?.cancel()
        travailMicro = null
        micro.arreter()
        hautParleur.arreter()
        canalMot?.close()
        canalMot = null
        canalSons?.close()
        canalSons = null
        ouvertLocal = false
        preroll.clear()
        aRejouer = null
        _etat.update {
            it.copy(
                phase = Phase.ARRET,
                conversation = false,
                motEntendu = false,
                score = 0f,
                message = "Écoute arrêtée.",
            )
        }
    }

    /** Termine tout : utilisé quand l'application se ferme pour de bon. */
    fun liberer() {
        arreter()
        detecteur = null
        portee.cancel()
    }

    // ---------------------------------------------------------------- association

    /** Ouvre l'attente d'association : le téléphone apparaîtra dans l'administration. */
    fun associer() {
        appairage?.fermer()
        val client = clientTls()
        _etat.update {
            it.copy(
                phase = Phase.APPAIRAGE,
                erreur = null,
                message = "Recherche de Cornelius sur ${reglages.adresse}…",
            )
        }
        appairage = Appairage(client, reglages.adresse, reglages.identifiantAppareil, object : Appairage.Ecoute {
            override fun enAttente() {
                _etat.update {
                    it.copy(
                        phase = Phase.EN_ATTENTE_ASSOCIATION,
                        message = "Ouvre l'administration de Cornelius, puis Satellites audio : " +
                            "ton téléphone attend. Clique « Associer ».",
                    )
                }
            }

            override fun associe(nom: String, jeton: String) {
                reglages.jeton = jeton
                reglages.nom = nom
                appairage?.fermer()
                appairage = null
                _etat.update { it.copy(nom = nom) }
                Log.i(TAG, "Association acceptée : $nom")
                demarrerAudio()
                connecter()
            }

            override fun clignote() = Unit

            override fun erreur(message: String) {
                _etat.update {
                    it.copy(
                        phase = Phase.ERREUR,
                        erreur = message,
                        message = "Association impossible. Vérifie l'adresse et que Cornelius est allumé.",
                    )
                }
            }
        })
        appairage?.demarrer()
    }

    fun oublierAssociation() {
        arreter()
        reglages.oublierAssociation()
        _etat.update {
            it.copy(phase = Phase.ARRET, nom = "", message = "Association oubliée. Relance l'écoute pour réassocier.")
        }
    }

    fun oublierCertificat() {
        reglages.oublierCertificat()
        arreter()
        _etat.update { it.copy(message = "Certificat oublié : il sera accepté à la prochaine connexion.") }
    }

    // ---------------------------------------------------------------- réglages

    fun changerAdresse(adresse: String) {
        val propre = adresse.trim().removePrefix("https://").removePrefix("wss://").trimEnd('/')
        val avecPort = if (propre.contains(":")) propre else "$propre:3443"
        if (avecPort == reglages.adresse) return
        reglages.adresse = avecPort
        reglages.oublierCertificat()
        if (actif) {
            arreter()
            demarrer()
        }
    }

    fun changerMode(mode: ModeEcoute) {
        if (mode == reglages.mode) return
        reglages.mode = mode
        ouvertLocal = mode == ModeEcoute.DIRECT
        preroll.clear()
        aRejouer = null
        _etat.update { it.copy(message = "Mode ${if (mode == ModeEcoute.DIRECT) "direct" else "économie"}.") }
    }

    fun reglerSeuil(seuil: Float) {
        reglages.seuil = seuil
        detecteur?.reglerSeuil(seuil)
    }

    /** Bouton de vie privée : le micro est coupé, mais l'écoute reste prête. */
    fun couperMicro(coupe: Boolean) {
        reglages.microCoupe = coupe
        _etat.update { it.copy(microCoupe = coupe) }
        if (coupe) {
            preroll.clear()
            aRejouer = null
            ouvertLocal = false
        }
        envoyer("mic") { put("muted", coupe) }
    }

    // ---------------------------------------------------------------- connexion

    private fun clientTls() = ConfianceCornelius.client({ reglages.empreinte }, { reglages.empreinte = it })

    private fun connecter() {
        if (!actif) return
        travailReconnexion?.cancel()
        travailReconnexion = null
        _etat.update { it.copy(phase = Phase.CONNEXION, erreur = null, message = "Connexion à ${reglages.adresse}…") }
        val nouveau = Satellite(
            clientTls(),
            reglages.adresse,
            reglages.jeton,
            object : Satellite.Ecoute {
                override fun ouvert() {
                    essais = 0
                    _etat.update {
                        it.copy(phase = Phase.CONNECTE, erreur = null, message = "Connecté.")
                    }
                    envoyer("hello") {
                        put("protocol", Protocole.VERSION)
                        put("firmware", Protocole.FIRMWARE)
                        put("aec", annihilationEcho)
                    }
                }

                override fun message(texte: String) = traiterMessage(texte)

                override fun audio(pcm: ShortArray) {
                    canalSons?.trySend(pcm)
                }

                override fun perdu(raison: String) {
                    if (!actif) return
                    satellite = null
                    ouvertLocal = false
                    ecouteServeur = false
                    _etat.update {
                        it.copy(phase = Phase.CONNEXION, conversation = false, message = "Connexion perdue : $raison")
                    }
                    if (reglages.jeton.isEmpty()) {
                        associer()
                    } else {
                        replanifier()
                    }
                }
            },
        )
        satellite = nouveau
        nouveau.connecter()
    }

    /** Réessaie avec un délai qui double à chaque échec, jusqu'à une minute. */
    private fun replanifier() {
        if (!actif || travailReconnexion?.isActive == true) return
        essais++
        val attente = minOf(60_000L, 1_000L shl minOf(essais, 6))
        travailReconnexion = portee.launch {
            delay(attente)
            if (actif) connecter()
        }
    }

    private fun traiterMessage(texte: String) {
        val message = runCatching { JSONObject(texte) }.getOrNull() ?: return
        when (message.optString("type")) {
            "welcome" -> {
                val nom = message.optString("name", reglages.nom)
                reglages.nom = nom
                _etat.update { it.copy(nom = nom, message = "Connecté à Cornelius.") }
            }

            "listen" -> {
                ecouteServeur = message.optBoolean("enabled", false)
                if (!ecouteServeur) {
                    ouvertLocal = false
                    preroll.clear()
                    aRejouer = null
                } else if (reglages.mode == ModeEcoute.DIRECT) {
                    ouvertLocal = true
                }
            }

            "session" -> {
                when (message.optString("state")) {
                    "connecting" -> {
                        preroll.clear()
                        _etat.update { it.copy(conversation = true, message = "Cornelius t'écoute…") }
                    }

                    "ready" -> _etat.update { it.copy(conversation = true, message = "Conversation ouverte.") }

                    "closed" -> {
                        _etat.update {
                            it.copy(conversation = false, motEntendu = false, message = "Conversation terminée.")
                        }
                        fermerEcouteLocale(1_200)
                    }
                }
            }

            "wake" -> {
                _etat.update { it.copy(motEntendu = true, message = "« Cornelius » entendu.") }
            }

            "busy" -> {
                _etat.update { it.copy(message = "Une conversation est déjà en cours ailleurs.") }
                fermerEcouteLocale(1_500)
            }

            "settings" -> {
                // Le volume affiché par Cornelius ne change pas celui du téléphone.
            }
        }
    }

    // ---------------------------------------------------------------- son

    private fun demarrerAudio() {
        if (!micro.demarrer()) {
            _etat.update {
                it.copy(phase = Phase.ERREUR, erreur = "Micro indisponible.", message = "Le micro n'a pas pu démarrer.")
            }
            return
        }
        annihilationEcho = micro.annuleEcho
        hautParleur.demarrer()
        demarrerLecture()
        preparerDetecteur()
        demarrerBoucleMicro()
    }

    private fun demarrerLecture() {
        canalSons?.close()
        val canal = Channel<ShortArray>(capacity = 32)
        canalSons = canal
        portee.launch(filSon) {
            for (pcm in canal) hautParleur.ecrire(pcm)
        }
    }

    private fun preparerDetecteur() {
        canalMot?.close()
        val canal = Channel<ShortArray>(capacity = 4)
        canalMot = canal
        portee.launch(filMot) {
            val pipeline = runCatching {
                WakePipeline(
                    contexte.assets.open("models/melspectrogram.onnx").readBytes(),
                    contexte.assets.open("models/embedding_model.onnx").readBytes(),
                    contexte.assets.open("models/cornelius.onnx").readBytes(),
                )
            }.onFailure { Log.e(TAG, "Modèles du mot d'appel illisibles.", it) }.getOrNull() ?: return@launch

            val calcul = DetecteurMotAppel(pipeline, reglages.seuil)
            detecteur = calcul
            for (bloc in canal) {
                val score = try {
                    calcul.analyser(bloc)
                } catch (erreur: Exception) {
                    Log.e(TAG, "Calcul du mot d'appel en échec.", erreur)
                    break
                }
                _etat.update { it.copy(score = score) }
                if (calcul.motEntendu(score)) motEntendu()
            }
            pipeline.close()
        }
    }

    private fun demarrerBoucleMicro() {
        if (travailMicro?.isActive == true) return
        travailMicro = portee.launch(filMicro) {
            val tampon = ShortArray(WakePipeline.BLOC)
            while (isActive && actif) {
                val lus = try {
                    micro.lire(tampon)
                } catch (erreur: Exception) {
                    Log.w(TAG, "Lecture du micro interrompue.", erreur)
                    break
                }
                if (lus < WakePipeline.BLOC) {
                    if (lus < 0) break
                    continue
                }
                traiterBloc(tampon.copyOf())
            }
        }
    }

    private fun traiterBloc(bloc: ShortArray) {
        val coupe = reglages.microCoupe
        if (!coupe) {
            when (reglages.mode) {
                ModeEcoute.DIRECT -> if (ecouteServeur) satellite?.envoyerAudio(bloc)
                ModeEcoute.ECONOMIE -> {
                    if (ouvertLocal && ecouteServeur) {
                        aRejouer?.let { garde ->
                            for (trame in garde) satellite?.envoyerAudio(trame)
                            aRejouer = null
                        }
                        satellite?.envoyerAudio(bloc)
                    } else if (ecouteServeur) {
                        preroll.addLast(bloc)
                        while (preroll.size > PREROLL_MAX) preroll.removeFirst()
                    }
                }
            }
        }
        canalMot?.trySend(bloc)
    }

    // ---------------------------------------------------------------- mot d'appel

    /** Appelé sur le fil de calcul quand le mot d'appel vient d'être entendu. */
    private fun motEntendu() {
        if (reglages.microCoupe || reglages.mode != ModeEcoute.ECONOMIE) {
            _etat.update { it.copy(motEntendu = true) }
            return
        }
        if (ouvertLocal || !ecouteServeur) return
        // Le rejeu est confié au fil du micro, pour que l'ordre des trames reste juste.
        // Il faut le poser avant « ouvertLocal » : c'est ce drapeau que lit le fil du micro.
        aRejouer = preroll.toList()
        preroll.clear()
        ouvertLocal = true
        _etat.update { it.copy(motEntendu = true, message = "« Cornelius » entendu, j'ouvre la conversation…") }
        // Le serveur n'analyse le son que si le micro n'est pas coupé de son point de vue.
        envoyer("mic") { put("muted", false) }
        portee.launch {
            delay(ATTENTE_OUVERTURE)
            if (ouvertLocal && !_etat.value.conversation) fermerEcouteLocale(0)
        }
    }

    private fun fermerEcouteLocale(delai: Long) {
        portee.launch {
            if (delai > 0) delay(delai)
            ouvertLocal = false
            preroll.clear()
            aRejouer = null
            detecteur?.reinitialiser()
            _etat.update { it.copy(motEntendu = false) }
        }
    }

    // ---------------------------------------------------------------- outils

    private fun envoyer(type: String, remplir: JSONObject.() -> Unit) {
        val message = JSONObject().put("type", type).apply(remplir)
        satellite?.envoyer(message.toString())
    }
}
