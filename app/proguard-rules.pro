# Règles R8/ProGuard spécifiques au projet (voir revue technique, point 8).
#
# Minification activée le 9 août 2026, testée sur device avant d'être considérée fiable -- les
# règles ci-dessous ciblent les bibliothèques connues pour casser silencieusement sous R8 faute
# de -keep explicite (réflexion, JNI, protobuf, sérialiseurs générés), pas une supposition en l'air.
# Chaque bloc cite sa raison. Ne pas retirer une règle sans revalider sur device (assembleRelease +
# installation + test manuel des trois protocoles réseau + tracking).

# --- Optimisation désactivée (shrinking + renommage gardés) ---
# Confirmé sur device (9 août 2026) : avec l'optimisation R8 active, l'app plantait au tout premier
# lancement -- java.lang.ExceptionInInitializerError dans com.google.mediapipe.framework.Graph.<clinit>,
# cause racine "IllegalStateException: no caller found on the stack for: fk0" (fk0 = FluentLogger de
# Guava Flogger, dépendance transitive de MediaPipe -- vérifié via le fichier mapping.txt généré par
# R8, pas supposé). Flogger détecte son appelant en parcourant la pile d'appels ; le reste de la
# trace (déobfusquée via mapping.txt) montre que la pile en question, au moment du crash, ne contient
# que du code applicatif tout à fait normal (MainViewModel.initializeTracking() et la machinerie
# standard des coroutines Kotlin, BaseContinuationImpl/DispatchedTask) -- l'optimiseur R8 a fusionné/
# inliné ces frames de coroutine (transformation correcte et sans danger en soi), ce qui prive
# Flogger d'une frame "appelant" reconnaissable. -dontoptimize désactive uniquement les
# transformations de bytecode (inlining, fusion de classes) responsables de ce genre de rupture,
# tout en gardant le shrinking (classes mortes supprimées) et le renommage (obfuscation) -- l'essentiel
# du gain de taille d'APK vient du premier, pas du second. Voir revue technique point 8 pour le
# résultat du nouveau test device après ce changement.
-dontoptimize

# --- MediaPipe Face Landmarker ---
# Utilise protobuf (réflexion pour le parsing de message) et des appels JNI natifs qui résolvent des
# signatures Java par nom de classe/méthode -- R8 peut renommer/supprimer ces symboles sans qu'aucune
# erreur de compilation ne le signale, l'échec n'apparaît qu'à l'exécution (crash natif ou silencieux).
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }

# Classes protobuf optionnelles (profiling de graphe, templates de graphe) référencées par le code
# interne de MediaPipe mais absentes de l'artefact tasks-vision utilisé ici (pas de la supposition :
# généré par R8 lui-même dans missing_rules.txt lors du premier build minifié, 9 août 2026) --
# jamais atteintes dans l'usage de ce projet (FaceLandmarker en LIVE_STREAM, pas de profiling).
-dontwarn com.google.mediapipe.proto.CalculatorProfileProto$CalculatorProfile
-dontwarn com.google.mediapipe.proto.GraphTemplateProto$CalculatorGraphTemplate

# --- ARCore (Augmented Faces, palier OPTIMAL) ---
# Même famille de risque (JNI natif), recommandation standard de la doc officielle ARCore pour R8.
-keep class com.google.ar.core.** { *; }

# --- javaosc-core (protocole VMC) ---
# Bibliothèque déjà responsable d'un crash réel sur ce projet à cause d'un appel réflexif fragile
# dans son bloc d'initialisation statique (LibraryInfo, voir revue technique point 38) -- même si
# VmcOscSender n'utilise plus les classes en cause, prudence sur le reste de la bibliothèque
# (OSCSerializer/OSCParser encore utilisés) tant que ce n'est pas explicitement vérifié sûr sous R8.
-keep class com.illposed.osc.** { *; }

# --- nv-websocket-client (VTube Studio Plugin API) ---
# Client WebSocket Java classique, pas de réflexion connue dans son usage ici -- pas de règle
# spécifique à ce stade, à revoir si un test device révèle un problème.

# --- kotlinx.serialization (protocole VTube Studio) ---
# Règles officielles recommandées par le projet (github.com/Kotlin/kotlinx.serialization, section
# "Android"), nécessaires même avec le plugin compilateur Kotlin actif : les sérialiseurs générés
# (`$serializer`, `Companion.serializer()`) sont résolus par nom à l'exécution par le runtime de
# sérialisation, pas seulement par des références directes visibles à la compilation.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$Companion Companion;
}
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
