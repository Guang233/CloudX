package com.guang.cloudx.ui.home

import android.os.Bundle
import androidx.activity.addCallback
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.button.MaterialButton
import com.guang.cloudx.BaseActivity
import com.guang.cloudx.R

class MainActivity : BaseActivity() {
    private val navButton by lazy {findViewById<MaterialButton>(R.id.navButton)}
    private val drawerLayout by lazy { findViewById<DrawerLayout>(R.id.drawer_layout) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        navButton.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        onBackPressedDispatcher.addCallback(this) {
            if (drawerLayout.isDrawerOpen(GravityCompat.START))
                drawerLayout.close()
            else
                finish()
        }
    }
}