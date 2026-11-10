package com.cornelius.satellite.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cornelius.satellite.Etat
import com.cornelius.satellite.Moteur
import com.cornelius.satellite.Phase
import com.cornelius.satellite.data.ModeEcoute
import kotlin.math.roundToInt

/**
 * L'écran unique : l'orbe, l'état, deux boutons, et les réglages derrière l'engrenage.
 */
@Composable
fun EcranCornelius(
    moteur: Moteur,
    demarrer: () -> Unit,
    arreter: () -> Unit,
) {
    val etat by moteur.etat.collectAsState()
    var reglagesOuverts by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(CouleursCornelius.Fond, CouleursCornelius.FondClair, CouleursCornelius.Fond),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cornelius",
                        style = MaterialTheme.typography.headlineMedium,
                        color = CouleursCornelius.Texte,
                    )
                    Text(
                        text = when {
                            etat.nom.isNotBlank() -> "Satellite « ${etat.nom} »"
                            else -> "Satellite du téléphone"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = CouleursCornelius.TexteFaible,
                    )
                }
                IconButton(onClick = { reglagesOuverts = !reglagesOuverts }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Réglages",
                        tint = if (reglagesOuverts) CouleursCornelius.Accent else CouleursCornelius.TexteFaible,
                    )
                }
            }

            Spacer(Modifier.height(36.dp))
            Orbe(etat)
            Spacer(Modifier.height(28.dp))

            Text(
                text = etat.message.ifBlank { "Prêt." },
                style = MaterialTheme.typography.bodyLarge,
                color = CouleursCornelius.Texte,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            etat.erreur?.let { erreur ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = erreur,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CouleursCornelius.Alerte,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(20.dp))
            IndicateurScore(etat)
            Spacer(Modifier.height(28.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (etat.phase == Phase.ARRET || etat.phase == Phase.ERREUR) {
                    Button(onClick = demarrer) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Démarrer l'écoute")
                    }
                } else {
                    OutlinedButton(onClick = arreter) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Arrêter")
                    }
                }
                OutlinedButton(
                    onClick = { moteur.couperMicro(!etat.microCoupe) },
                    enabled = etat.phase != Phase.ARRET,
                ) {
                    Icon(
                        if (etat.microCoupe) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (etat.microCoupe) "Micro coupé" else "Couper")
                }
            }

            if (reglagesOuverts) {
                Spacer(Modifier.height(28.dp))
                Reglages(moteur, etat.phase)
            }
        }
    }
}

/** L'anneau du miroir : il s'allume quand Cornelius écoute et pulse quand il parle. */
@Composable
private fun Orbe(etat: Etat) {
    val transition = rememberInfiniteTransition(label = "orbe")
    val pulsation by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulsation",
    )
    val intensite = (0.25f + etat.score.coerceIn(0f, 1f) * 0.75f)
    val actif = etat.phase != Phase.ARRET
    val couleur = if (etat.microCoupe) CouleursCornelius.TexteFaible else CouleursCornelius.Accent

    Canvas(modifier = Modifier.size(220.dp)) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val rayon = size.minDimension * 0.30f * pulsation
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(couleur.copy(alpha = if (actif) intensite * 0.55f else 0.08f), Color.Transparent),
                center = centre,
                radius = size.minDimension * 0.5f,
            ),
            radius = size.minDimension * 0.5f,
            center = centre,
        )
        drawCircle(
            color = couleur.copy(alpha = if (actif) 0.22f else 0.06f),
            radius = rayon * 0.78f,
            center = centre,
        )
        drawCircle(
            color = couleur.copy(alpha = if (actif) 0.9f else 0.25f),
            radius = rayon,
            center = centre,
            style = Stroke(width = 2.dp.toPx()),
        )
        if (etat.conversation) {
            drawCircle(
                color = couleur.copy(alpha = 0.5f),
                radius = rayon * 1.22f,
                center = centre,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

@Composable
private fun IndicateurScore(etat: Etat) {
    val mode = if (etat.microCoupe) "Micro coupé" else "Mot d'appel « Cornelius »"
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = mode,
                style = MaterialTheme.typography.labelSmall,
                color = CouleursCornelius.TexteFaible,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(etat.score * 100).roundToInt()} %",
                style = MaterialTheme.typography.labelSmall,
                color = CouleursCornelius.TexteFaible,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { etat.score.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = CouleursCornelius.Accent,
            trackColor = CouleursCornelius.AccentFaible.copy(alpha = 0.25f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Reglages(moteur: Moteur, phase: Phase) {
    var adresse by rememberSaveable { mutableStateOf(moteur.reglages.adresse) }
    var mode by remember { mutableStateOf(moteur.reglages.mode) }
    var seuil by remember { mutableStateOf(moteur.reglages.seuil) }
    var empreinte by remember { mutableStateOf(moteur.reglages.empreinte) }
    // L'empreinte n'existe qu'après la première connexion : on la relit à chaque changement d'état.
    LaunchedEffect(phase) { empreinte = moteur.reglages.empreinte }

    Card(
        colors = CardDefaults.cardColors(containerColor = CouleursCornelius.FondClair),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Réglages",
                style = MaterialTheme.typography.titleLarge,
                color = CouleursCornelius.Texte,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = adresse,
                onValueChange = { adresse = it },
                label = { Text("Adresse de Cornelius") },
                supportingText = { Text("Par exemple 192.168.50.3:3443") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                moteur.changerAdresse(adresse)
                adresse = moteur.reglages.adresse
            }) {
                Text("Enregistrer l'adresse et se reconnecter")
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Quand le téléphone écoute",
                style = MaterialTheme.typography.bodyMedium,
                color = CouleursCornelius.TexteFaible,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == ModeEcoute.ECONOMIE,
                    onClick = {
                        mode = ModeEcoute.ECONOMIE
                        moteur.changerMode(ModeEcoute.ECONOMIE)
                    },
                    label = { Text("Économie") },
                )
                FilterChip(
                    selected = mode == ModeEcoute.DIRECT,
                    onClick = {
                        mode = ModeEcoute.DIRECT
                        moteur.changerMode(ModeEcoute.DIRECT)
                    },
                    label = { Text("Direct") },
                )
            }
            Text(
                text = if (mode == ModeEcoute.ECONOMIE) {
                    "Le téléphone cherche « Cornelius » lui-même et n'envoie du son qu'ensuite : " +
                        "moins de réseau et de batterie."
                } else {
                    "Le son part en continu vers Cornelius, comme un satellite du commerce : " +
                        "c'est lui qui cherche le mot d'appel."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = CouleursCornelius.TexteFaible,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Seuil du mot d'appel : ${(seuil * 100).roundToInt()} %",
                style = MaterialTheme.typography.bodyMedium,
                color = CouleursCornelius.TexteFaible,
            )
            Slider(
                value = seuil,
                onValueChange = { seuil = it },
                onValueChangeFinished = { moteur.reglerSeuil(seuil) },
                valueRange = 0.2f..0.9f,
                steps = 13,
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Certificat de Cornelius",
                style = MaterialTheme.typography.bodyMedium,
                color = CouleursCornelius.TexteFaible,
            )
            Text(
                text = empreinte.ifBlank { "Pas encore vu : il sera retenu à la première connexion." },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = CouleursCornelius.Texte,
            )
            Row {
                TextButton(onClick = {
                    moteur.oublierCertificat()
                    empreinte = moteur.reglages.empreinte
                }) { Text("Oublier le certificat") }
                TextButton(onClick = { moteur.associer() }) { Text("Réassocier") }
            }

            Spacer(Modifier.height(4.dp))
            Surface(color = Color.Transparent) {
                Text(
                    text = "Appareil : ${moteur.reglages.identifiantAppareil}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CouleursCornelius.TexteFaible,
                )
            }
        }
    }
}
