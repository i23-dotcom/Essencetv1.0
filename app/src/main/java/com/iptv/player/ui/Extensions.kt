package com.iptv.player.ui

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

/** Simple text-changed listener without pulling in the full androidx.core ktx text extension. */
fun EditText.doAfterTextChangedCompat(action: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            action(s?.toString().orEmpty())
        }
    })
}
