package com.guyiome.androidmocap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.guyiome.androidmocap.R
import com.guyiome.androidmocap.tracking.TongueCalibrationPhase
import kotlinx.coroutines.delay

/** Délai d'affichage de la confirmation "Calibré ✓" avant fermeture automatique -- fermer
 *  immédiatement était trop brutal (retour explicite de l'utilisateur). */
private const val CALIBRATION_DONE_DISPLAY_MS = 1500L

/**
 * Calibration personnelle de l'étage 3 : deux enregistrements de quelques secondes ("langue
 * dehors" puis "langue rentrée") qui produisent les deux vecteurs de référence utilisés par
 * [com.guyiome.androidmocap.tracking.classifyTongueState]. Même esprit UX que la calibration de
 * pose neutre (`MainHud`, `calibrationCountdownSeconds`) -- compte à rebours puis capture -- mais
 * étendu à un enregistrement multi-secondes accumulé plutôt qu'un instantané unique, voir
 * `TongueCalibrationRecordingState.kt`.
 *
 * Aperçu caméra volontairement laissé visible (pas de fond opaque, contrairement aux autres écrans
 * de réglages) -- l'utilisateur doit pouvoir voir comment il est cadré pendant qu'il tire la
 * langue/ouvre la mâchoire, pas seulement lire des instructions sur fond noir.
 *
 * Le message de phase et le bouton d'action doivent rester dans un coin/bord constant de l'écran
 * **tel que perçu par l'utilisateur**, quelle que soit l'orientation physique du téléphone --
 * retour utilisateur explicite : une simple rotation du contenu (comme [BlendshapePanel], qui reste
 * ancré au même coin de la mise en page portrait sous-jacente) ne suffit pas, il faut aussi
 * **changer d'ancrage** selon l'orientation. [snapToRotationBucket] donne un palier {0,90,180,270} ;
 * pour chaque palier, `messageAlignment`/`buttonAlignment` ci-dessous choisissent le coin/bord de la
 * mise en page portrait sous-jacente qui, une fois la rotation physique appliquée, retombe sur le
 * coin/bord voulu à l'écran (message : toujours en haut à gauche perçu ; bouton : toujours en bas
 * perçu) -- dérivé géométriquement de la convention `OrientationEventListener` déjà utilisée pour
 * `panelRotationDegrees` (`MainScreen.kt`), pas revérifié visuellement sur les 4 paliers (aucun
 * device dans ce sandbox) : à confirmer sur device, un seul palier à corriger si l'un d'eux part
 * dans le mauvais sens plutôt que de tout redevoir.
 */
@Composable
fun TongueCalibrationScreen(
    phase: TongueCalibrationPhase,
    secondsRemaining: Int?,
    isCalibrated: Boolean,
    recordingDurationMs: Long,
    classificationMargin: Float,
    iconRotationDegrees: Float,
    onStartCalibration: () -> Unit,
    onCancel: () -> Unit,
    onSetRecordingDurationMs: (Long) -> Unit,
    onSetClassificationMargin: (Float) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    // État local, volontairement non hoisté/persisté -- même patron que AdvancedSettingsScreen (voir
    // DebugPanelUnlock.kt) : le geste (7 taps sur le titre) ne coûte que quelques secondes à refaire.
    var debugUnlock by remember { mutableStateOf(DebugPanelUnlockState()) }

    // Fermeture automatique une fois la calibration réellement terminée : la machine à état
    // (TongueCalibrationRecordingState) traite DONE et retombe sur IDLE dans le même tick côté
    // MainViewModel, donc cet écran n'observe jamais DONE tel quel -- seule la transition
    // RECORDING_TONGUE_IN -> IDLE, combinée à isCalibrated déjà vrai, signale une fin réelle plutôt
    // qu'un simple retour à l'écran de menu. Limite connue et assumée (refonte cosmétique
    // uniquement, pas de nouveau signal côté ViewModel) : annuler précisément pendant cette
    // dernière phase, alors qu'une calibration antérieure existait déjà, déclenche aussi cette
    // fermeture -- rare, sans conséquence fonctionnelle.
    //
    // Fermeture immédiate jugée trop brutale (retour utilisateur) : justFinished affiche une
    // confirmation "Calibré ✓" (sans bouton, écran figé) pendant CALIBRATION_DONE_DISPLAY_MS avant
    // d'appeler onClose().
    // Palier de rotation + ancrages dérivés -- voir kdoc de la fonction pour le raisonnement complet
    // (pourquoi un ancrage différent par palier, pas juste une rotation de contenu).
    val rotationBucket = snapToRotationBucket(iconRotationDegrees.toInt())
    val contentRotationZ = when (rotationBucket) {
        90 -> -90f
        270 -> 90f
        180 -> 180f
        else -> 0f
    }
    val messageAlignment = when (rotationBucket) {
        90 -> Alignment.BottomStart
        180 -> Alignment.BottomEnd
        270 -> Alignment.TopEnd
        else -> Alignment.TopStart
    }
    val buttonAlignment = when (rotationBucket) {
        90 -> Alignment.CenterEnd
        180 -> Alignment.TopCenter
        270 -> Alignment.CenterStart
        else -> Alignment.BottomCenter
    }

    var previousPhase by remember { mutableStateOf(phase) }
    var justFinished by remember { mutableStateOf(false) }
    LaunchedEffect(phase) {
        if (previousPhase == TongueCalibrationPhase.RECORDING_TONGUE_IN &&
            phase == TongueCalibrationPhase.IDLE &&
            isCalibrated
        ) {
            justFinished = true
            delay(CALIBRATION_DONE_DISPLAY_MS)
            onClose()
        }
        previousPhase = phase
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Message de phase -- pastille semi-transparente (même style que le bandeau d'icônes de
        // MainHud) plutôt qu'un plein écran noir, pour laisser l'aperçu caméra visible en dessous.
        // Ancrage (messageAlignment) ET rotation du contenu (contentRotationZ) changent tous les
        // deux avec le palier -- reste perçu en haut à gauche quelle que soit l'orientation physique,
        // voir kdoc de la fonction. Pivot centré par défaut (pas de TransformOrigin custom) : chaque
        // ancrage est déjà le bon coin pour ce palier, pas besoin de compenser un débordement comme
        // BlendshapePanel (qui, lui, garde un ancrage fixe).
        //
        // La flèche de retour vit DANS ce même bloc, au bout de la ligne de titre -- même convention
        // que les autres écrans de réglages (titre à gauche, flèche à droite d'une même Row), et
        // suit ainsi la même rotation/ancrage que le reste du contenu.
        Column(
            modifier = Modifier
                .align(messageAlignment)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp)
                .graphicsLayer(rotationZ = contentRotationZ)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .widthIn(max = 300.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.tongue_calibration_title),
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            debugUnlock = debugUnlock.registerTap(System.currentTimeMillis())
                        },
                )
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = Color.White,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            when {
                // Confirmation de fin, affichée seule (ni étapes à venir, ni bouton en dessous)
                // pendant CALIBRATION_DONE_DISPLAY_MS avant la fermeture automatique.
                justFinished -> {
                    Text(
                        stringResource(R.string.tongue_calibration_status_calibrated),
                        color = Color(0xFF9FE7B0),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                phase == TongueCalibrationPhase.IDLE || phase == TongueCalibrationPhase.DONE -> {
                    // Explication complète déjà lue avant d'arriver ici (bouton "Calibrer" de
                    // ExperimentalFeaturesScreen) -- un déroulé court des étapes à venir remplace
                    // le paragraphe d'origine, retour utilisateur explicite.
                    Text(
                        stringResource(R.string.tongue_calibration_steps_overview),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (isCalibrated) {
                            stringResource(R.string.tongue_calibration_status_calibrated)
                        } else {
                            stringResource(R.string.tongue_calibration_status_never)
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                else -> {
                    val statusText = when (phase) {
                        TongueCalibrationPhase.PREPARE_TONGUE_OUT -> stringResource(R.string.tongue_calibration_status_prepare_out)
                        TongueCalibrationPhase.RECORDING_TONGUE_OUT -> stringResource(R.string.tongue_calibration_status_recording_out)
                        TongueCalibrationPhase.PREPARE_TONGUE_IN -> stringResource(R.string.tongue_calibration_status_prepare_in)
                        TongueCalibrationPhase.RECORDING_TONGUE_IN -> stringResource(R.string.tongue_calibration_status_recording_in)
                        // IDLE/DONE exclus par la condition du when englobant -- smart-cast, when
                        // exhaustif sans eux, pas de else nécessaire.
                    }
                    Text(
                        statusText,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            // Compte à rebours : gros chiffres en préparation (l'utilisateur doit encore se
            // positionner, un repère bien visible aide), petits pendant la prise de vues elle-même
            // (ne doit pas distraire du geste à tenir) -- demande explicite de l'utilisateur. Masqué
            // pendant justFinished (secondsRemaining est déjà null à ce stade de toute façon).
            if (secondsRemaining != null && !justFinished) {
                val isPreparePhase = phase == TongueCalibrationPhase.PREPARE_TONGUE_OUT ||
                    phase == TongueCalibrationPhase.PREPARE_TONGUE_IN
                Spacer(Modifier.height(if (isPreparePhase) 8.dp else 4.dp))
                Text(
                    "${secondsRemaining}s",
                    color = Color.White,
                    style = if (isPreparePhase) MaterialTheme.typography.displayLarge else MaterialTheme.typography.titleLarge,
                )
            }
        }

        // Bouton d'action + panneau debug -- ancrage (buttonAlignment) ET rotation (contentRotationZ)
        // changent tous les deux avec le palier, même raisonnement que le message de phase : reste
        // perçu en bas de l'écran quelle que soit l'orientation physique, voir kdoc de la fonction.
        // ⚠️ Volontairement PAS de fillMaxWidth() ici (avant rotation) : un bloc large comme tout
        // l'écran, une fois tourné à ±90°, devient un pavé aussi haut que l'écran est large --
        // exactement le bug du bouton "Recalibrer" démesuré constaté sur device. Largeur bornée à la
        // place, même logique que BlendshapePanel qui évite pour la même raison tout élément large
        // avant rotation.
        if (!justFinished) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(buttonAlignment)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(20.dp)
                    .graphicsLayer(rotationZ = contentRotationZ)
                    .widthIn(min = 220.dp, max = 280.dp),
            ) {
                if (debugUnlock.unlocked) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.tongue_calibration_debug_section_title),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                        )

                        // Unité de base, pas la durée réelle d'une phase (chaque phase la multiplie
                        // par son propre facteur -- voir TONGUE_OUT_RECORDING_MULTIPLIER/
                        // TONGUE_IN_RECORDING_MULTIPLIER) -- pas de rebuild pour retenter un réglage.
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(R.string.tongue_calibration_debug_duration, recordingDurationMs / 1000f),
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onSetRecordingDurationMs((recordingDurationMs - 500L).coerceAtLeast(500L)) }) {
                                Text("-0.5s")
                            }
                            Spacer(Modifier.width(4.dp))
                            TextButton(onClick = { onSetRecordingDurationMs(recordingDurationMs + 500L) }) {
                                Text("+0.5s")
                            }
                        }

                        // Marge de classification (voir TongueEmbeddingClassifier.classifyTongueState).
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(R.string.tongue_calibration_debug_margin, classificationMargin),
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onSetClassificationMargin((classificationMargin - 0.01f).coerceAtLeast(0f)) }) {
                                Text("-0.01")
                            }
                            Spacer(Modifier.width(4.dp))
                            TextButton(onClick = { onSetClassificationMargin(classificationMargin + 0.01f) }) {
                                Text("+0.01")
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.tongue_calibration_debug_hint),
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                when (phase) {
                    TongueCalibrationPhase.IDLE, TongueCalibrationPhase.DONE -> {
                        Button(onClick = onStartCalibration, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (isCalibrated) {
                                    stringResource(R.string.tongue_calibration_button_recalibrate)
                                } else {
                                    stringResource(R.string.tongue_calibration_button_start)
                                }
                            )
                        }
                    }
                    TongueCalibrationPhase.PREPARE_TONGUE_OUT,
                    TongueCalibrationPhase.RECORDING_TONGUE_OUT,
                    TongueCalibrationPhase.PREPARE_TONGUE_IN,
                    TongueCalibrationPhase.RECORDING_TONGUE_IN -> {
                        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.tongue_calibration_button_cancel))
                        }
                    }
                }
            }
        }
    }
}
