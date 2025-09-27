package com.guang.cloudx.ui.login

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.guang.cloudx.BaseActivity
import com.guang.cloudx.R
import com.guang.cloudx.logic.utils.showSnackBar
import kotlin.getValue

class LoginActivity : BaseActivity(), View.OnClickListener {
    private val textInput by lazy { findViewById<EditText>(R.id.cookieEditText) }
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

        textInput.setText(prefs.getCookie())

        topAppBar.setNavigationOnClickListener(this)
        button.setOnClickListener(this)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.saveButton -> {
                prefs.putCookie(textInput.text.toString())
                button.showSnackBar("已保存")
            }
            R.id.topAppBar -> finish()
        }
    }

}