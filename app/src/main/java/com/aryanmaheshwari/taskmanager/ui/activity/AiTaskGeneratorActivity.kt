package com.aryanmaheshwari.taskmanager.ui.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.aryanmaheshwari.taskmanager.MainActivity

/**
 * Stub — this Activity is superseded by [AiTaskGeneratorBottomSheet].
 * Kept registered in the Manifest to avoid breaking existing navigation
 * references, but immediately redirects back to [MainActivity].
 */
class AiTaskGeneratorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Immediately finish; the BottomSheet in MainActivity handles generation.
        finish()
    }
}
