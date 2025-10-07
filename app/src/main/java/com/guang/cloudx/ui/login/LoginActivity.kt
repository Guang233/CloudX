package com.guang.cloudx.ui.login

import android.os.Bundle
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.guang.cloudx.BaseActivity
import com.guang.cloudx.R

class LoginActivity : BaseActivity() {
    private val cookieEditText by lazy { findViewById<EditText>(R.id.cookieEditText) }
    private val userIdEditText by lazy { findViewById<EditText>(R.id.userIdEditText) }
    private val button by lazy { findViewById<MaterialButton>(R.id.saveButton) }

    private val topAppBar by lazy { findViewById<MaterialToolbar>(R.id.topAppBar) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        userIdEditText.setText(prefs.getUserId())
        cookieEditText.setText(prefs.getCookie())

        topAppBar.setNavigationOnClickListener { finish() }
        button.setOnClickListener { _ ->
            prefs.putUserId(userIdEditText.text.toString())
            prefs.putCookie(cookieEditText.text.toString())
            finish()
        }
    }

}