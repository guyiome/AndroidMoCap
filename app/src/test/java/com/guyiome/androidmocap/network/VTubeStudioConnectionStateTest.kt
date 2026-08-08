package com.guyiome.androidmocap.network

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Couvre [nextVTubeStudioConnectionState] -- pure, testable en JVM. Ne couvre pas `VTubeStudioSender`
 * (socket réel, non testable ici) ni le vrai comportement du serveur VTube Studio.
 */
class VTubeStudioConnectionStateTest {

    private fun next(state: VTubeStudioConnectionState, event: VTubeStudioConnectionEvent) =
        nextVTubeStudioConnectionState(state, event)

    @Test
    fun `Connect depuis Disconnected passe a Connecting`() {
        assertEquals(
            VTubeStudioConnectionState.Connecting,
            next(VTubeStudioConnectionState.Disconnected, VTubeStudioConnectionEvent.Connect),
        )
    }

    @Test
    fun `Connect depuis Failed permet une reconnexion`() {
        assertEquals(
            VTubeStudioConnectionState.Connecting,
            next(VTubeStudioConnectionState.Failed("erreur réseau"), VTubeStudioConnectionEvent.Connect),
        )
    }

    @Test
    fun `SocketOpened sans token stocke va vers AwaitingUserApproval`() {
        assertEquals(
            VTubeStudioConnectionState.AwaitingUserApproval,
            next(VTubeStudioConnectionState.Connecting, VTubeStudioConnectionEvent.SocketOpened(hasStoredToken = false)),
        )
    }

    @Test
    fun `SocketOpened avec token stocke va directement vers Authenticating`() {
        assertEquals(
            VTubeStudioConnectionState.Authenticating,
            next(VTubeStudioConnectionState.Connecting, VTubeStudioConnectionEvent.SocketOpened(hasStoredToken = true)),
        )
    }

    @Test
    fun `AuthTokenApproved depuis AwaitingUserApproval va vers Authenticating`() {
        assertEquals(
            VTubeStudioConnectionState.Authenticating,
            next(VTubeStudioConnectionState.AwaitingUserApproval, VTubeStudioConnectionEvent.AuthTokenApproved("token-abc")),
        )
    }

    @Test
    fun `AuthTokenDenied depuis AwaitingUserApproval va vers Failed avec la raison`() {
        val result = next(
            VTubeStudioConnectionState.AwaitingUserApproval,
            VTubeStudioConnectionEvent.AuthTokenDenied("Utilisateur a refusé"),
        )
        assertEquals(VTubeStudioConnectionState.Failed("Utilisateur a refusé"), result)
    }

    @Test
    fun `Authenticated depuis Authenticating va vers Authenticated`() {
        assertEquals(
            VTubeStudioConnectionState.Authenticated,
            next(VTubeStudioConnectionState.Authenticating, VTubeStudioConnectionEvent.Authenticated),
        )
    }

    @Test
    fun `AuthenticationFailed depuis Authenticating va vers Failed (ex token revoque)`() {
        val result = next(VTubeStudioConnectionState.Authenticating, VTubeStudioConnectionEvent.AuthenticationFailed("Token invalide"))
        assertEquals(VTubeStudioConnectionState.Failed("Token invalide"), result)
    }

    @Test
    fun `ParametersReady depuis Authenticated va vers ParametersRegistered`() {
        assertEquals(
            VTubeStudioConnectionState.ParametersRegistered,
            next(VTubeStudioConnectionState.Authenticated, VTubeStudioConnectionEvent.ParametersReady),
        )
    }

    @Test
    fun `un APIError individuel pendant la creation de parametres ne fait plus echouer la connexion`() {
        // Une collision de nom avec un autre plugin (ex. VBridger, mêmes noms ARKit) ne doit plus
        // faire échouer toute la connexion -- géré en amont dans VTubeStudioSender (le paramètre en
        // échec est simplement compté comme "traité"), donc plus d'événement dédié ici : aucun
        // événement de ce type ne fait sortir Authenticated sauf ParametersReady.
        assertEquals(
            VTubeStudioConnectionState.Authenticated,
            next(VTubeStudioConnectionState.Authenticated, VTubeStudioConnectionEvent.AuthTokenDenied("Refusé")),
        )
    }

    @Test
    fun `StoredTokenRejected depuis Authenticating va vers AwaitingUserApproval`() {
        // Jeton stocké révoqué côté VTube Studio -- on retente avec un nouveau popup plutôt que
        // d'abandonner la connexion (voir VTubeStudioSender).
        assertEquals(
            VTubeStudioConnectionState.AwaitingUserApproval,
            next(VTubeStudioConnectionState.Authenticating, VTubeStudioConnectionEvent.StoredTokenRejected),
        )
    }

    @Test
    fun `SocketFailed est valide depuis n'importe quel etat, y compris ParametersRegistered`() {
        val result = next(VTubeStudioConnectionState.ParametersRegistered, VTubeStudioConnectionEvent.SocketFailed("Connexion perdue"))
        assertEquals(VTubeStudioConnectionState.Failed("Connexion perdue"), result)
    }

    @Test
    fun `Disconnect est valide depuis n'importe quel etat`() {
        listOf(
            VTubeStudioConnectionState.Connecting,
            VTubeStudioConnectionState.AwaitingUserApproval,
            VTubeStudioConnectionState.Authenticating,
            VTubeStudioConnectionState.Authenticated,
            VTubeStudioConnectionState.ParametersRegistered,
            VTubeStudioConnectionState.Failed("x"),
        ).forEach { state ->
            assertEquals(
                VTubeStudioConnectionState.Disconnected,
                next(state, VTubeStudioConnectionEvent.Disconnect),
            )
        }
    }

    @Test
    fun `un evenement hors sequence est ignore, l'etat ne bouge pas`() {
        // ParametersReady ne veut rien dire tant qu'on n'est pas encore authentifié.
        assertEquals(
            VTubeStudioConnectionState.AwaitingUserApproval,
            next(VTubeStudioConnectionState.AwaitingUserApproval, VTubeStudioConnectionEvent.ParametersReady),
        )
    }

    @Test
    fun `Failed ne bouge pas tout seul sans Connect ni Disconnect`() {
        val failed = VTubeStudioConnectionState.Failed("erreur")
        assertEquals(failed, next(failed, VTubeStudioConnectionEvent.AuthTokenApproved("token")))
        assertEquals(failed, next(failed, VTubeStudioConnectionEvent.Authenticated))
    }
}
