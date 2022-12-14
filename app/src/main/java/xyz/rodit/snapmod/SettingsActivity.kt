package xyz.rodit.snapmod

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import xyz.rodit.xposed.SettingsActivity
import xyz.rodit.xposed.utils.PathUtils
import java.io.File
import java.util.function.Consumer

class SettingsActivity : SettingsActivity(R.xml.root_preferences) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val minUpdateDelta = PreferenceManager.getDefaultSharedPreferences(this)
            .getString("update_frequency", "0")!!.toLong()
        if (minUpdateDelta >= 0) {
            val updateCheckFile = File(filesDir, ".update")
            val lastUpdateCheck =
                if (updateCheckFile.exists()) updateCheckFile.lastModified() else 0
            if (System.currentTimeMillis() - lastUpdateCheck >= minUpdateDelta) {
                performUpdateCheck()
                updateCheckFile.writeBytes(byteArrayOf())
            }
        }
    }

    private fun performUpdateCheck() {
        updates.checkForUpdates("RevealedSoulEven", "SnapMod-new")
    }

    override fun onCreatePreferences(fragment: SettingsFragment) {
        fragment.findPreference<Preference>("installation_status")?.apply {
            summary = getInstallationSummary(false)
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                showInfoDialog()
                true
            }
        }

        fragment.findPreference<Preference>("donations")?.apply {
            onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(getString(R.string.donations_link))
                        )
                    )
                    true
                }
        }

        fragment.findPreference<Preference>("check_for_updates")?.apply {
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                performUpdateCheck()
                true
            }
        }

        setNumericInput(fragment, "public_dp_resolution")
        setNumericInput(fragment, "location_share_lat")
        setNumericInput(fragment, "location_share_long")
        setNumericInput(fragment, "audio_playback_speed")
        setNumericInput(fragment, "custom_video_fps")
        setNumericInput(fragment, "custom_video_bitrate")

        (fragment.findPreference<Preference>("hidden_friends") as EditTextPreference?)?.apply {
            setOnBindEditTextListener {
                it.imeOptions = EditorInfo.IME_ACTION_NONE
                it.inputType = InputType.TYPE_TEXT_FLAG_MULTI_LINE
                it.isSingleLine = false
                it.setSelection(it.text.length)
            }
        }

        val camera = getSystemService(CAMERA_SERVICE) as CameraManager
        val characteristics = camera.getCameraCharacteristics(camera.cameraIdList[0])
        val config = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        config?.let { c ->
            val sizes = c.getOutputSizes(ImageFormat.JPEG)
            val strings = sizes.map { s -> "${s.width}x${s.height}" }.toTypedArray()
            sequenceOf(
                "custom_image_resolution",
                "custom_video_resolution"
            ).map { fragment.findPreference<ListPreference>(it) }
                .filterNotNull()
                .forEach {
                    it.entries = strings + "Default"
                    it.entryValues = strings + "0"
                }
        }

        fragment.findPreference<Preference>("camera_readme")?.apply {
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                showCameraReadme()
                true
            }
        }
    }

    private fun setNumericInput(fragment: SettingsFragment, name: String) {
        (fragment.findPreference<Preference>(name) as EditTextPreference?)?.apply {
            setOnBindEditTextListener { t: EditText ->
                t.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
        }
    }

    private fun showInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.installation_status_title)
            .setMessage(getInstallationSummary(true))
            .show()
    }

    private fun showCameraReadme() {
        AlertDialog.Builder(this)
            .setTitle(R.string.camera_title)
            .setMessage(R.string.camera_dialog_description)
            .show()
    }

    private fun getInstallationSummary(detailed: Boolean): Spannable {
        val builder = SpannableStringBuilder()
        try {
            val info = packageManager.getPackageInfo(Shared.SNAPCHAT_PACKAGE, 0)
            val possible =
                PathUtils.getPossibleMappingFiles(this, info.versionCode.toString() + ".json")
            val mappings = possible.firstOrNull { obj: File -> obj.exists() }
            val supported = mappings != null
            builder.append("Snapchat Version: ")
                .append(
                    info.versionName + " (" + info.versionCode + ")",
                    ForegroundColorSpan(if (supported) Color.GREEN else Color.RED),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                .append('\n')
            if (supported) {
                builder.append(
                    "Supported",
                    ForegroundColorSpan(Color.GREEN),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                if (detailed) {
                    builder.append("\n\nMappings found at ").append(mappings.toString())
                }
            } else {
                builder.append(
                    "Unsupported",
                    ForegroundColorSpan(Color.RED),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                if (detailed) {
                    builder.append("\n\nNo mappings found at ")
                    possible.forEach(Consumer { f: File -> builder.append(f.path).append('\n') })
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            builder.append(
                "Error getting Snapchat package info.",
                ForegroundColorSpan(Color.RED),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        if (!detailed) {
            builder.append("\nTap for more info.")
        }

        return builder
    }
}