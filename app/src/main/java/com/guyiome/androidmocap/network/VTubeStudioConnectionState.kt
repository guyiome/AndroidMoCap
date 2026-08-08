package com.guyiome.androidmocap.network

/**
 * Machine à état pure pour le cycle de connexion à l'API Plugin de VTube Studio (voir
 * [VTubeStudioProtocol] pour le protocole JSON, `VTubeStudioSender` pour le socket réel qui pilote
 * cette machine en réaction aux messages entrants). Même famille que `CalibrationAnomalyState`/
 * `ThermalThrottleState` : [nextVTubeStudioConnectionState] est pure et testée sans device.
 *
 * `next()` ignore silencieusement les événements qui ne s'appliquent pas à l'état courant (reste
 * sur place) plutôt que de planter -- même discipline défensive que le reste de ce projet, un
 * événement réseau reçu hors séquence (ex. une réponse en retard après une déconnexion) ne doit
 * jamais faire crasher l'app.
 */
sealed interface VTubeStudioConnectionState {
    /** Aucune tentative de connexion en cours, ou connexion fermée/jamais démarrée. */
    data object Disconnected : VTubeStudioConnectionState

    /** Socket WebSocket en cours d'ouverture. */
    data object Connecting : VTubeStudioConnectionState

    /**
     * `AuthenticationTokenRequest` envoyé, en attente que l'utilisateur approuve (ou refuse) le
     * popup affiché par VTube Studio -- peut durer indéfiniment tant que l'utilisateur ne répond
     * pas, ce n'est pas un timeout applicatif de ce côté.
     */
    data object AwaitingUserApproval : VTubeStudioConnectionState

    /** `AuthenticationRequest` envoyé (token neuf ou déjà stocké), réponse en attente. */
    data object Authenticating : VTubeStudioConnectionState

    /** Authentifié, création des paramètres (un par blendshape connu) en cours. */
    data object Authenticated : VTubeStudioConnectionState

    /** Prêt à streamer : authentifié et tous les paramètres créés/mis à jour côté VTube Studio. */
    data object ParametersRegistered : VTubeStudioConnectionState

    /** Échec à n'importe quelle étape -- [reason] est un message destiné à l'UI, pas une exception. */
    data class Failed(val reason: String) : VTubeStudioConnectionState
}

internal sealed interface VTubeStudioConnectionEvent {
    /** Nouvelle tentative de connexion demandée par l'utilisateur -- depuis `Disconnected`/`Failed`. */
    data object Connect : VTubeStudioConnectionEvent

    /** Socket ouvert -- [hasStoredToken] détermine ré-auth directe ou nouveau popup d'autorisation. */
    data class SocketOpened(val hasStoredToken: Boolean) : VTubeStudioConnectionEvent
    data class AuthTokenApproved(val token: String) : VTubeStudioConnectionEvent
    data class AuthTokenDenied(val reason: String) : VTubeStudioConnectionEvent
    data object Authenticated : VTubeStudioConnectionEvent
    data class AuthenticationFailed(val reason: String) : VTubeStudioConnectionEvent
    data object ParametersReady : VTubeStudioConnectionEvent
    data class ParameterCreationFailed(val reason: String) : VTubeStudioConnectionEvent

    /** Fermeture/erreur du socket lui-même, valide depuis n'importe quel état. */
    data class SocketFailed(val reason: String) : VTubeStudioConnectionEvent

    /** Déconnexion explicite (utilisateur ou `onCleared()`), valide depuis n'importe quel état. */
    data object Disconnect : VTubeStudioConnectionEvent
}

internal fun nextVTubeStudioConnectionState(
    state: VTubeStudioConnectionState,
    event: VTubeStudioConnectionEvent,
): VTubeStudioConnectionState {
    // Ces deux événements sont valides depuis n'importe quel état -- traités avant le `when` par état.
    if (event is VTubeStudioConnectionEvent.Disconnect) return VTubeStudioConnectionState.Disconnected
    if (event is VTubeStudioConnectionEvent.SocketFailed) return VTubeStudioConnectionState.Failed(event.reason)

    return when (state) {
        VTubeStudioConnectionState.Disconnected, is VTubeStudioConnectionState.Failed -> when (event) {
            VTubeStudioConnectionEvent.Connect -> VTubeStudioConnectionState.Connecting
            else -> state
        }

        VTubeStudioConnectionState.Connecting -> when (event) {
            is VTubeStudioConnectionEvent.SocketOpened ->
                if (event.hasStoredToken) VTubeStudioConnectionState.Authenticating
                else VTubeStudioConnectionState.AwaitingUserApproval
            else -> state
        }

        VTubeStudioConnectionState.AwaitingUserApproval -> when (event) {
            is VTubeStudioConnectionEvent.AuthTokenApproved -> VTubeStudioConnectionState.Authenticating
            is VTubeStudioConnectionEvent.AuthTokenDenied -> VTubeStudioConnectionState.Failed(event.reason)
            else -> state
        }

        VTubeStudioConnectionState.Authenticating -> when (event) {
            VTubeStudioConnectionEvent.Authenticated -> VTubeStudioConnectionState.Authenticated
            is VTubeStudioConnectionEvent.AuthenticationFailed -> VTubeStudioConnectionState.Failed(event.reason)
            else -> state
        }

        VTubeStudioConnectionState.Authenticated -> when (event) {
            VTubeStudioConnectionEvent.ParametersReady -> VTubeStudioConnectionState.ParametersRegistered
            is VTubeStudioConnectionEvent.ParameterCreationFailed -> VTubeStudioConnectionState.Failed(event.reason)
            else -> state
        }

        // État terminal stable : seuls Disconnect/SocketFailed (déjà gérés plus haut) en sortent.
        VTubeStudioConnectionState.ParametersRegistered -> state
    }
}
