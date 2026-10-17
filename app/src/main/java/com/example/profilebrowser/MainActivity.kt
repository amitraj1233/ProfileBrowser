package com.example.profilebrowser

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature

class MainActivity : AppCompatActivity() {
    private lateinit var list: ListView
    private var profiles = mutableListOf<Profile>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Profiles (max $MAX_PROFILES)"
        list = ListView(this)
        setContentView(list)
        list.setOnItemClickListener { _, _, i, _ ->
            startActivity(Intent(this, BrowserActivity::class.java).putExtra("id", profiles[i].id))
        }
        list.setOnItemLongClickListener { _, _, i, _ -> editDialog(profiles[i]); true }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        profiles = Store.load(this)
        val rows = profiles.map {
            val px = if (it.proxy.isBlank()) "no proxy" else "proxy on"
            "${it.name}\n${it.os} | $px\n${it.deviceId}"
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
        title = "Profiles ${profiles.size}/$MAX_PROFILES  (tap=open, long-press=edit)"
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add("New profile").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (profiles.size >= MAX_PROFILES) {
            Toast.makeText(this, "Maximum $MAX_PROFILES profiles hi ban sakte hain", Toast.LENGTH_LONG).show()
        } else editDialog(null)
        return true
    }

    private fun field(hint: String, value: String, multiline: Boolean = false): EditText = EditText(this).apply {
        this.hint = hint
        setText(value)
        if (multiline) {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
        } else inputType = InputType.TYPE_CLASS_TEXT
    }

    private fun editDialog(existing: Profile?) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val name = field("Profile name", existing?.name ?: "")
        val os = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, PRESETS.keys.toList())
            setSelection(PRESETS.keys.indexOf(existing?.os ?: "Windows").coerceAtLeast(0))
        }
        val proxy = field("Proxy: http://user:pass@ip:port", existing?.proxy ?: "")
        val url = field("Start URL", existing?.url ?: "https://www.google.com")
        val script = field("Custom JS (optional, har page par chalega)", existing?.script ?: "", true)
        val regen = CheckBox(this).apply { text = "Naya fingerprint / device ID banao" }
        box.addView(name)
        box.addView(TextView(this).apply { text = "Device OS" })
        box.addView(os)
        box.addView(proxy)
        box.addView(url)
        box.addView(script)
        if (existing != null) box.addView(regen)

        val b = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "New profile" else "Edit profile")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val n = name.text.toString().trim()
                val chosenOs = os.selectedItem as String
                try {
                    parseProxy(proxy.text.toString())
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                if (n.isEmpty()) {
                    Toast.makeText(this, "Name daalo", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (existing == null) {
                    profiles.add(Profile.create(n, chosenOs, proxy.text.toString().trim(), url.text.toString().trim(), script.text.toString()))
                } else {
                    val osChanged = existing.os != chosenOs
                    existing.name = n
                    existing.os = chosenOs
                    existing.proxy = proxy.text.toString().trim()
                    existing.url = url.text.toString().trim()
                    existing.script = script.text.toString()
                    if (regen.isChecked || osChanged) existing.reroll()
                }
                Store.save(this, profiles)
                refresh()
            }
        if (existing != null) {
            b.setNeutralButton("Delete") { _, _ ->
                AlertDialog.Builder(this)
                    .setMessage("'${existing.name}' aur uska data delete karein?")
                    .setPositiveButton("Delete") { _, _ ->
                        try {
                            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                                ProfileStore.getInstance().deleteProfile(existing.webProfileName)
                            }
                        } catch (e: Exception) { /* profile in use / not created yet */ }
                        profiles.remove(existing)
                        Store.save(this, profiles)
                        refresh()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        b.show()
    }
}
