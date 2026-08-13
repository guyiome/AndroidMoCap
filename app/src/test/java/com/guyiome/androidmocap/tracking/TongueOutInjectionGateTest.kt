package com.guyiome.androidmocap.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Couvre [TongueOutInjectionState.next]/[TongueOutInjectionState.confirmed] -- purs, JVM. */
class TongueOutInjectionGateTest {

    @Test
    fun `etat initial non confirme`() {
        assertFalse(TongueOutInjectionState().confirmed())
    }

    @Test
    fun `TONGUE_OUT incremente le compteur`() {
        val state = TongueOutInjectionState().next(TongueEmbeddingClassification.TONGUE_OUT)
        assertEquals(1, state.consecutiveOutFrames)
        assertFalse(state.confirmed())
    }

    @Test
    fun `confirme a partir de 2 TONGUE_OUT consecutifs (seuil par defaut, abaisse le 13 aout 2026)`() {
        var state = TongueOutInjectionState()
        state = state.next(TongueEmbeddingClassification.TONGUE_OUT)
        assertFalse(state.confirmed())
        state = state.next(TongueEmbeddingClassification.TONGUE_OUT)
        assertTrue(state.confirmed())
    }

    @Test
    fun `une frame isolee ne suffit pas -- reproduit le residu observe le 13 aout 2026`() {
        var state = TongueOutInjectionState()
            .next(TongueEmbeddingClassification.TONGUE_IN)
            .next(TongueEmbeddingClassification.TONGUE_OUT) // frame isolee, comme 16:00:04.279
            .next(TongueEmbeddingClassification.TONGUE_IN)
        assertFalse(state.confirmed())
    }

    @Test
    fun `une paire consecutive confirme desormais -- compromis assume, seuil abaisse a 2 le 13 aout 2026`() {
        // Contrairement au seuil d'origine (3) : ce cas precis (comme 16:00:10.759/10.888) est
        // reste un residu connu et accepte, voir kdoc de DEFAULT_TONGUE_OUT_INJECTION_CONSECUTIVE_FRAMES.
        var state = TongueOutInjectionState()
            .next(TongueEmbeddingClassification.TONGUE_OUT)
            .next(TongueEmbeddingClassification.TONGUE_OUT)
        assertTrue(state.confirmed())
    }

    @Test
    fun `TONGUE_IN remet le compteur a zero`() {
        var state = TongueOutInjectionState()
        repeat(2) { state = state.next(TongueEmbeddingClassification.TONGUE_OUT) }
        state = state.next(TongueEmbeddingClassification.TONGUE_IN)
        assertEquals(0, state.consecutiveOutFrames)
    }

    @Test
    fun `UNDECIDED remet le compteur a zero`() {
        var state = TongueOutInjectionState()
        repeat(2) { state = state.next(TongueEmbeddingClassification.TONGUE_OUT) }
        state = state.next(TongueEmbeddingClassification.UNDECIDED)
        assertEquals(0, state.consecutiveOutFrames)
    }

    @Test
    fun `null remet le compteur a zero`() {
        var state = TongueOutInjectionState()
        repeat(2) { state = state.next(TongueEmbeddingClassification.TONGUE_OUT) }
        state = state.next(null)
        assertEquals(0, state.consecutiveOutFrames)
    }

    @Test
    fun `seuil personnalise respecte`() {
        var state = TongueOutInjectionState()
        repeat(4) { state = state.next(TongueEmbeddingClassification.TONGUE_OUT) }
        assertFalse(state.confirmed(requiredConsecutiveFrames = 5))
        state = state.next(TongueEmbeddingClassification.TONGUE_OUT)
        assertTrue(state.confirmed(requiredConsecutiveFrames = 5))
    }

    @Test
    fun `une tenue soutenue reste confirmee frame apres frame`() {
        var state = TongueOutInjectionState()
        repeat(10) {
            state = state.next(TongueEmbeddingClassification.TONGUE_OUT)
            if (it >= 1) assertTrue("frame $it devrait etre confirmee", state.confirmed())
        }
    }

    // --- tongueOutInjectionShouldUpdate -- ajoutee le 13 aout 2026 (revue de code) : avant cette
    // garde, l'appelant appelait next() a chaque frame camera, y compris celles ou aucune
    // classification n'avait ete tentee (throttle embed()), remettant le compteur a zero a tort.

    @Test
    fun `etage 1 ferme -- doit mettre a jour (vrai signal negatif)`() {
        assertTrue(tongueOutInjectionShouldUpdate(stage1Open = false, classification = null))
    }

    @Test
    fun `etage 1 ferme avec une classification non-null -- doit quand meme mettre a jour`() {
        // Cas impossible en pratique (classifyTongueState n'est jamais appele si l'etage 1 est
        // ferme), mais la fonction reste correcte par construction : stage1Open=false suffit seul.
        assertTrue(tongueOutInjectionShouldUpdate(stage1Open = false, classification = TongueEmbeddingClassification.TONGUE_OUT))
    }

    @Test
    fun `etage 1 ouvert mais classification non tentee -- ne doit PAS mettre a jour, reproduit le bug`() {
        // Le coeur du correctif : throttle embed(), pas encore calibre, ou echec de recadrage --
        // aucune information nouvelle, l'etat doit etre gele plutot que reinitialise.
        assertFalse(tongueOutInjectionShouldUpdate(stage1Open = true, classification = null))
    }

    @Test
    fun `etage 1 ouvert avec une vraie classification -- doit mettre a jour, quelle qu'elle soit`() {
        assertTrue(tongueOutInjectionShouldUpdate(stage1Open = true, classification = TongueEmbeddingClassification.TONGUE_OUT))
        assertTrue(tongueOutInjectionShouldUpdate(stage1Open = true, classification = TongueEmbeddingClassification.TONGUE_IN))
        assertTrue(tongueOutInjectionShouldUpdate(stage1Open = true, classification = TongueEmbeddingClassification.UNDECIDED))
    }

    @Test
    fun `une frame throttlee au milieu d'une tenue ne remet plus le compteur a zero`() {
        // Reproduit le scenario reel : etage 1 ouvert en continu, mais une classification fraiche
        // seulement une frame sur deux (throttle embed()) -- avant le correctif, la frame throttlee
        // (classification=null) remettait consecutiveOutFrames a zero via next(null) systematique.
        var state = TongueOutInjectionState()
        val stage1Open = true

        // Frame 1 : classification reussie, TONGUE_OUT.
        if (tongueOutInjectionShouldUpdate(stage1Open, TongueEmbeddingClassification.TONGUE_OUT)) {
            state = state.next(TongueEmbeddingClassification.TONGUE_OUT)
        }
        assertEquals(1, state.consecutiveOutFrames)

        // Frame 2 : throttlee, aucune classification tentee -- doit etre ignoree, pas resetee.
        if (tongueOutInjectionShouldUpdate(stage1Open, null)) {
            state = state.next(null)
        }
        assertEquals(1, state.consecutiveOutFrames)
        assertFalse(state.confirmed())

        // Frame 3 : classification reussie, TONGUE_OUT de nouveau -- confirme desormais.
        if (tongueOutInjectionShouldUpdate(stage1Open, TongueEmbeddingClassification.TONGUE_OUT)) {
            state = state.next(TongueEmbeddingClassification.TONGUE_OUT)
        }
        assertEquals(2, state.consecutiveOutFrames)
        assertTrue(state.confirmed())
    }
}
