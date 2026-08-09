package com.guyiome.androidmocap

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.guyiome.androidmocap.ui.MainScreen
import com.guyiome.androidmocap.ui.theme.AndroidMoCapTheme

// AppCompatActivity (pas ComponentActivity) : requis par AppCompatDelegate.setApplicationLocales()
// sous Compose pour que le sélecteur de langue en-app fonctionne (point 30, doc officielle Android
// -- "If you're using Compose with setApplicationLocales, you must extend your activity from
// AppCompatActivity"), voir aussi le thème Theme.AppCompat.DayNight requis (themes.xml) et
// AppLocalesMetadataHolderService (AndroidManifest.xml) pour la persistance sur toutes versions.
class MainActivity : AppCompatActivity() {

    private var hasCameraPermission by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Empêche le verrouillage automatique tant que l'app est au premier plan -- CameraX coupe
        // la caméra dès que l'Activity passe en arrière-plan (verrouillage écran inclus).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        hasCameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

        setContent {
            AndroidMoCapTheme {
                MainScreen(
                    hasCameraPermission = hasCameraPermission,
                    onRequestPermission = { requestPermissionLauncher.launch(Manifest.permission.CAMERA) },
                )
            }
        }
    }
}
