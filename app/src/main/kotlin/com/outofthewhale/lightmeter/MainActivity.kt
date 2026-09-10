package com.outofthewhale.lightmeter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner

private val android.content.Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "light_meter",
)

class MainActivity : ComponentActivity() {

    private var granted by mutableStateOf(false)
    private lateinit var viewModel: MeterViewModel

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { allowed -> granted = allowed }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A meter is read at arm's length while the other hand holds a camera;
        // letting the screen sleep mid-reading would be its own small betrayal.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        viewModel = ViewModelProvider(
            this as ViewModelStoreOwner,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MeterViewModel(SettingsStore(applicationContext.dataStore)) as T
            },
        )[MeterViewModel::class.java]

        granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestCamera.launch(Manifest.permission.CAMERA)

        setContent { MeterScreen(viewModel = viewModel, cameraGranted = granted) }
    }

    override fun onResume() {
        super.onResume()
        granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        viewModel.onResumed()
    }
}
