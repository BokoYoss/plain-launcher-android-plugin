package org.godotengine.plainlauncher

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder


object RequestCodes {
    const val REQUEST_SET_STORAGE = 1
    const val REQUEST_GET_DOWNLOADED_IMAGE = 2
    const val REQUEST_PERMISSIONS = 3
    const val LAUNCH_APPLICATION = 4
    const val REQUEST_WEB_IMAGE = 5
}

class GodotAndroidPlugin(godot: Godot): GodotPlugin(godot) {

    override fun getPluginName() = BuildConfig.GODOT_PLUGIN_NAME
    override fun getPluginSignals(): Set<SignalInfo> {
        val signals: MutableSet<SignalInfo> = mutableSetOf()
        signals.add(
            SignalInfo(
                "configure_storage_location",
                String::class.java
            ),
        )
        signals.add(
            SignalInfo(
                "image_downloaded",
                String::class.java
            ),
        )
        signals.add(
            SignalInfo(
                "failure_to_launch",
                String::class.java
            ),
        )
        signals.add(
            SignalInfo(
                "text_input_complete",
                String::class.java
            ),
        )
        return signals
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>?,
        grantResults: IntArray
    ) {
        if (requestCode == RequestCodes.REQUEST_PERMISSIONS) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //Permission Granted
                Log.i(pluginName, "Permissions granted.")
            } else {
                Log.i(pluginName, "Permissions request failed.")
            }
        }
    }

    override fun onMainActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == RequestCodes.REQUEST_SET_STORAGE) {
            Log.i(pluginName, "GOT RESULT FOR " + requestCode + " WITH RETURN CODE " + resultCode + " WITH DATA " + data?.data?.path)

            if (resultCode != Activity.RESULT_OK || data == null) {
                Log.e(pluginName, "Error when using storage selector. ErrorCode: " + resultCode + " Data: " + data?.dataString)
                emitSignal("configure_storage_location", "FAILURE")
                return
            }
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

            activity?.contentResolver?.takePersistableUriPermission(data.data!!, takeFlags)

            val contentResolver = activity?.contentResolver

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
              if (!Environment.isExternalStorageManager()) {
                  Log.i(pluginName, "Not external storage manager")
              } else {
                  Log.i(pluginName, "Is external storage manager")
              }
            }

            emitSignal("configure_storage_location", data?.data?.path ?: "FAILURE")
        } else if (requestCode == RequestCodes.REQUEST_GET_DOWNLOADED_IMAGE) {
            Log.i(pluginName, "GOT RESULT FOR $requestCode WITH RETURN CODE $resultCode WITH DATA ${data?.data?.path}")
            if (resultCode != Activity.RESULT_OK || data?.data == null) {
                emitSignal("image_downloaded", "")
                return
            }
            try {
                val inputStream = activity?.contentResolver?.openInputStream(data.data!!)
                if (inputStream == null) {
                    emitSignal("image_downloaded", "")
                    return
                }
                val tempFile = java.io.File(activity?.cacheDir, "chosen_art.tmp")
                tempFile.outputStream().use { output -> inputStream.copyTo(output) }
                inputStream.close()
                emitSignal("image_downloaded", tempFile.absolutePath)
            } catch (e: Exception) {
                Log.e(pluginName, "Failed to copy chosen file: ${e.message}")
                emitSignal("image_downloaded", "")
            }
        }
        else if (requestCode == RequestCodes.REQUEST_WEB_IMAGE) {
            val path = if (resultCode == Activity.RESULT_OK) data?.getStringExtra("path") ?: "" else ""
            emitSignal("image_downloaded", path)
        }
        else if (requestCode == RequestCodes.REQUEST_PERMISSIONS) {
            if (SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    // perform action when allow permission success
                } else {
                    Toast.makeText(
                        activity?.applicationContext,
                        "File permission granted!",
                        Toast.LENGTH_SHORT
                    ).show();
                }
            }
        }
        /**
        else if (requestCode == RequestCodes.LAUNCH_APPLICATION) {
            super.onMainActivityResult(requestCode, resultCode, data)
            Log.i(
                pluginName,
                "GOT RESULT FOR " + requestCode + " WITH RETURN CODE " + resultCode + " WITH DATA " + data?.data?.path
            )
            if (resultCode != Activity.RESULT_OK) {
                Log.e(pluginName, "Failed to launch application: " + resultCode.toString() + " data: " + data?.component)
                emitSignal("failure_to_launch", resultCode.toString())
            }
        }
        **/
    }

    override fun onMainRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>?,
        grantResults: IntArray?
    ) {
        if (requestCode == RequestCodes.REQUEST_PERMISSIONS) {
            if (grantResults!!.size == 2 && grantResults!![0] == PackageManager.PERMISSION_GRANTED && grantResults!![1] == PackageManager.PERMISSION_GRANTED) {
                //Permission Granted
                Log.i(pluginName, "Permissions granted.")
            } else {
                Log.i(pluginName, "Permissions request failed.")
            }
        }
    }

    @UsedByGodot
    private fun chooseFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, "/mnt/sdcard/Downloads")
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity?.startActivityForResult(intent, RequestCodes.REQUEST_GET_DOWNLOADED_IMAGE)
    }

    @UsedByGodot
    private fun chooseStorageDirectory() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, "/mnt/sdcard/PlainLauncher")
            putExtra(DocumentsContract.EXTRA_PROMPT, "Select storage")
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity?.startActivityForResult(intent, RequestCodes.REQUEST_SET_STORAGE)
    }

    private fun getPrimaryStoragePath(): String {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            val storageManager: StorageManager =
                activity?.getSystemService(Context.STORAGE_SERVICE) as StorageManager

            storageManager.storageVolumes?.forEach { volume ->
                if (volume.isPrimary) {
                    return volume.toString()
                }
            }
        }
        return "/storage/emulated/0"
    }

    private fun getLegacyExternalStoragePath(): String? {
        activity?.getExternalFilesDirs(null)?.forEach { path ->
            if (!path.absolutePath.contains("emulated")) {
                // We just want /storage/<SOME CD CARD ID>
                var splitPath = path.absolutePath.split("/")
                if (splitPath.size >= 3) {
                    return "/storage/" + splitPath[2]
                }
            }
        }
        return null
    }

    @UsedByGodot
    private fun hasFilePermissions(): Boolean {
        return SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
    }

    @UsedByGodot
    private fun requestFilePermissions() {
        if (SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.setData(
                    Uri.parse(
                        java.lang.String.format(
                            "package:%s",
                            activity?.applicationContext?.packageName
                        )
                    )
                )
                activity?.startActivityForResult(intent, RequestCodes.REQUEST_PERMISSIONS)
            } catch (e: Exception) {
                val intent = Intent()
                intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                activity?.startActivityForResult(intent, RequestCodes.REQUEST_PERMISSIONS)
            }
        } else {
            activity?.requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), RequestCodes.REQUEST_PERMISSIONS)
        }
    }

    @UsedByGodot
    private fun pathToRemovableStorage(): String? {
        return getExternalStoragePath()
    }
    private fun getExternalStoragePath(): String? {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val storageManager: StorageManager =
                activity?.getSystemService(Context.STORAGE_SERVICE) as StorageManager

            storageManager.storageVolumes?.forEach { volume ->
                val directory = volume.directory
                if (volume.isRemovable && volume.state == Environment.MEDIA_MOUNTED && directory != null) {
                    return directory.toString()
                }
            }
        } else {
            var legacyStorage = getLegacyExternalStoragePath()
            if (legacyStorage != null) {
                Log.i(pluginName, "Got legacy external path " + legacyStorage)
                return legacyStorage
            }
        }
        return null
    }

    @UsedByGodot
    private fun createStorage(location: String = "internal") {
        /**
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, "/mnt/sdcard/PlainLauncher")
        putExtra(DocumentsContract.EXTRA_PROMPT, "Select storage")
        }
        runOnUiThread {
        Toast.makeText(
        activity, "Please make 'PlainLauncher' directory",
        Toast.LENGTH_LONG
        ).show()
        }
         **/
        val pathToUse: String = getPrimaryStoragePath()
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            putExtra(Intent.EXTRA_TITLE, "PlainLauncher")
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (location == "internal") {
            emitSignal("configure_storage_location", "/storage/emulated/0/PlainLauncher")
            return
        } else {
            val externalPath = getExternalStoragePath()
            if (externalPath == null) {
                emitSignal("configure_storage_location", "NOT_FOUND")
                return
            }
            val directory: File = File(externalPath + "/PlainLauncher")
            Log.i(pluginName, "Attempting to create dir at " + directory.path)
            val result = directory.mkdirs()
            Log.i(pluginName, "Create dirs at " + directory.path + " result: " + result)
            if (directory.exists()) {
                emitSignal("configure_storage_location", externalPath + "/PlainLauncher")
                return
            }
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,externalPath + "/PlainLauncher")
        }
        /**
        runOnUiThread {
            Toast.makeText(
                activity, "Press 'SAVE' to make and use PlainLauncher directory",
                Toast.LENGTH_LONG
            ).show()
        }
        intent.setType("vnd.android.document/directory")
        activity?.startActivityForResult(intent, RequestCodes.REQUEST_SET_STORAGE)
        **/
    }

    @UsedByGodot
    private fun showTextInput(prompt: String, currentValue: String, isPassword: Boolean) {
        activity?.runOnUiThread {
            val editText = android.widget.EditText(activity)
            editText.setText(currentValue)
            editText.inputType = if (isPassword) {
                android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                android.text.InputType.TYPE_CLASS_TEXT
            }
            val dialog = android.app.AlertDialog.Builder(activity!!)
                .setTitle(prompt)
                .setView(editText)
                .setPositiveButton("OK") { _, _ ->
                    emitSignal("text_input_complete", editText.text.toString())
                }
                .setNegativeButton("Cancel") { _, _ ->
                    emitSignal("text_input_complete", "")
                }
                .setOnCancelListener {
                    emitSignal("text_input_complete", "")
                }
                .create()
            dialog.show()
            editText.requestFocus()
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
    }

    @UsedByGodot
    private fun launchWebImagePicker(game: String, system: String, source: String) {
        val searchUrl = buildSearchUrl(game, system, source)
        val intent = Intent(activity, WebImagePickerActivity::class.java).apply {
            putExtra("searchUrl", searchUrl)
            putExtra("gameName", game)
        }
        activity?.startActivityForResult(intent, RequestCodes.REQUEST_WEB_IMAGE)
    }

    @UsedByGodot
    private fun launchBrowserForDownload(game: String, system: String, source: String) {
        val urlString = buildSearchUrl(game, system, source)
        val launcher = Intent(Intent.ACTION_VIEW, Uri.parse(urlString))
        launcher.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        Log.i(pluginName, "Attempting image search request for $source: $urlString")
        activity?.startActivity(launcher)
    }

    private fun buildSearchUrl(game: String, system: String, source: String): String {
        return when (source.lowercase()) {
            "tgdb" -> "https://thegamesdb.net/search.php?name=" + URLEncoder.encode(game, "utf-8")
            "duckduckgo" -> "https://duckduckgo.com/?t=h_&iax=images&ia=images&q=" + URLEncoder.encode(game, "utf-8")
            "launchbox" -> "https://gamesdb.launchbox-app.com/games/results/" + URLEncoder.encode(game, "utf-8").replace("+", "%20")
            "steamgriddb" -> "https://www.steamgriddb.com/search/grids?term=" + URLEncoder.encode(game, "utf-8")
            else -> "https://www.google.com/search?tbm=isch&q=" + URLEncoder.encode("$game $system box art", "utf-8")
        }
    }

    @UsedByGodot
    private fun launchPackage(pkg: String): String {
        val launcher: Intent? = activity?.packageManager?.getLaunchIntentForPackage(pkg)
        if (launcher == null) {
            Log.e(pluginName, "No launch intent for " + pkg)
            return pkg + " not installed."
        }
        try {
            activity?.startActivity(launcher)
        } catch (e: ActivityNotFoundException) {
            Log.e(pluginName, pkg + " not found.")
            return pkg + " not found."
        } catch (e: Exception) {
            return pkg + " failed to launch: " + e.message
        }
        return ""
    }

    @UsedByGodot
    private fun getInstalledAppList(): String {
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val packageManager = activity?.packageManager
        if (packageManager == null) {
            Log.w("PlainLauncher", "Unable to get package manager")
            return "{}"
        }
        val rawAppList: List<ApplicationInfo>? = activity?.packageManager?.getInstalledApplications(0)
        if (rawAppList == null) {
            Log.w("PlainLauncher", "Unable to get installed applications")
            return "{}"
        }
        val apps = mutableListOf<Pair<String, String>>()
        for (appInfo: ApplicationInfo in rawAppList!!) {
            if (activity?.packageManager?.getLaunchIntentForPackage(appInfo.packageName) == null) {
                continue
            }
            apps.add(Pair(appInfo.loadLabel(packageManager).toString(), appInfo.packageName))
        }
        val labelCounts = apps.groupingBy { it.first }.eachCount()
        val results = JSONObject()
        for ((label, pkg) in apps) {
            val key = if (labelCounts[label]!! > 1) "$label ($pkg)" else label
            results.put(key, pkg)
        }
        return results.toString()
    }

    @UsedByGodot
    private fun openAppSpecificSettings(pkg: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:" + pkg)
        activity?.startActivity(intent)
    }

    override fun onMainResume() {
        super.onMainResume()
        Log.i(pluginName, "RESUMING")
        // On cold boot as a launcher the GL surface isn't ready when focus is
        // first granted, leaving a black screen. Invalidating the decor view
        // after a short delay nudges Android to redraw without touching the GL
        // context (which would race with Godot's own resume and crash).
        activity?.window?.decorView?.postDelayed({
            activity?.window?.decorView?.invalidate()
        }, 500)
    }

    /**
     * Example showing how to declare a method that's used by Godot.
     *
     * Shows a 'Hello World' toast.
     */
    private fun providerUri(path: String, targetPackage: String): Uri? {
        if (path == "") {
            return null
        }
        try {
            val uri = FileProvider.getUriForFile(activity!!, "plain.launcher.fileprovider", File(path))
            if (targetPackage != "") {
                activity?.grantUriPermission(targetPackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Log.i(pluginName, "Created file provider uri: " + uri.toString())
            return uri
        } catch (e: Exception) {
            Log.e(pluginName, "Failed to make file provider: " + e.toString(), e)
            return null
        }
    }

    private fun putTypedExtra(intent: Intent, key: String, value: Any, resolve: (String) -> String) {
        when (value) {
            is Boolean -> intent.putExtra(key, value)
            is Int -> intent.putExtra(key, value)
            is Long -> intent.putExtra(key, value)
            is Double -> intent.putExtra(key, value)
            is JSONArray -> intent.putExtra(key, Array(value.length()) { resolve(value.optString(it)) })
            is JSONObject -> {
                val type = value.optString("type", "string")
                val raw = value.opt("value")
                val text = resolve((raw ?: "").toString()).trim()
                try {
                    when (type) {
                        "string" -> intent.putExtra(key, text)
                        "int" -> intent.putExtra(key, text.toInt())
                        "long" -> intent.putExtra(key, text.toLong())
                        "float" -> intent.putExtra(key, text.toFloat())
                        "double" -> intent.putExtra(key, text.toDouble())
                        "bool" -> intent.putExtra(key, text.lowercase() == "true" || text == "1")
                        "uri" -> intent.putExtra(key, Uri.parse(text))
                        "string_array" -> {
                            if (raw is JSONArray) {
                                intent.putExtra(key, Array(raw.length()) { resolve(raw.optString(it)) })
                            } else {
                                intent.putExtra(key, text.split(",").map { it.trim() }.toTypedArray())
                            }
                        }
                        else -> throw IllegalArgumentException("unknown extra type '" + type + "' for " + key)
                    }
                } catch (e: NumberFormatException) {
                    throw IllegalArgumentException("extra " + key + " expects " + type + " but got '" + text + "'")
                }
            }
            else -> intent.putExtra(key, resolve(value.toString()))
        }
    }

    @UsedByGodot
    private fun launchIntent(serializedIntent: String): String? {
        val intentMap: JSONObject
        try {
            intentMap = JSONObject(serializedIntent)
        } catch (e: JSONException) {
            Log.e(pluginName, "Invalid intent: " + serializedIntent)
            return "Invalid intent config: " + e.message
        }

        var packageName = intentMap.optString("package")
        if (packageName != null && packageName != "") {
            return launchPackage(packageName)
        }
        var action = intentMap.optString("action", Intent.ACTION_MAIN)

        val intent = Intent(action)

        var command: String = "am start -a " + action + " "

        try {
            var componentPackage = intentMap.getString("componentPackage")
            var componentClass = intentMap.getString("componentClass")
            var componentName = ComponentName(
                componentPackage,
                componentClass
            )
            intent.component = componentName
            command += "-n " + componentName.flattenToString()
        } catch (e: JSONException) {
            Log.w(pluginName, "Missing component path: " + serializedIntent)
        }

        val targetPackage = intentMap.optString("componentPackage")
        val gamePath = intentMap.optString("gamePath")
        var gameUri: String? = null
        val resolve = { value: String ->
            if (value.contains("{game_uri}")) {
                if (gameUri == null) {
                    gameUri = providerUri(gamePath, targetPackage)?.toString() ?: ""
                }
                value.replace("{game_uri}", gameUri ?: "")
            } else {
                value
            }
        }

        var dataUri: Uri? = null
        val data = resolve(intentMap.optString("data"))
        if (data != "") {
            dataUri = Uri.parse(data)
        }
        val providedFile = intentMap.optString("providedFile")
        if (providedFile != "") {
            val uri = providerUri(providedFile, targetPackage)
            if (uri != null) {
                dataUri = uri
                intent.putExtra("uri", providedFile)
            }
        }
        val mimeType = intentMap.optString("type")
        if (dataUri != null && mimeType != "") {
            intent.setDataAndType(dataUri, mimeType)
        } else if (dataUri != null) {
            intent.setData(dataUri)
        } else if (mimeType != "") {
            intent.setType(mimeType)
        }
        command += " -d \"" + dataUri.toString() + "\" -t \"" + mimeType + "\" "

        val categories = mutableListOf<String>()
        val category = intentMap.optString("category")
        if (category != "") {
            categories.add(category)
        }
        val categoryArray = intentMap.optJSONArray("categories")
        if (categoryArray != null) {
            for (i in 0 until categoryArray.length()) {
                categories.add(categoryArray.optString(i))
            }
        }
        for (name in categories) {
            intent.addCategory(name)
            command += " -c \"" + name + "\" "
        }

        val extraMap = intentMap.optJSONObject("extras")
        if (extraMap != null) {
            try {
                for (extraKey in extraMap.keys()) {
                    putTypedExtra(intent, extraKey, extraMap.get(extraKey), resolve)
                    command += " -e " + extraKey + " \"" + extraMap.get(extraKey).toString() + "\" "
                }
            } catch (e: IllegalArgumentException) {
                return "Invalid intent config: " + e.message
            }
        }

        Log.i(pluginName, command)

        val flagNameMap = mapOf(
            "FLAG_ACTIVITY_NEW_TASK" to Intent.FLAG_ACTIVITY_NEW_TASK,
            "FLAG_ACTIVITY_RESET_TASK_IF_NEEDED" to Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            "FLAG_ACTIVITY_SINGLE_TOP" to Intent.FLAG_ACTIVITY_SINGLE_TOP,
            "FLAG_ACTIVITY_CLEAR_TOP" to Intent.FLAG_ACTIVITY_CLEAR_TOP,
            "FLAG_ACTIVITY_REORDER_TO_FRONT" to Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            "FLAG_ACTIVITY_CLEAR_TASK" to Intent.FLAG_ACTIVITY_CLEAR_TASK,
            "FLAG_ACTIVITY_NO_HISTORY" to Intent.FLAG_ACTIVITY_NO_HISTORY,
            "FLAG_GRANT_READ_URI_PERMISSION" to Intent.FLAG_GRANT_READ_URI_PERMISSION,
            "FLAG_GRANT_WRITE_URI_PERMISSION" to Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val flagsArray = intentMap.optJSONArray("flags")
        if (flagsArray != null) {
            var flags = Intent.FLAG_ACTIVITY_NEW_TASK  // always required for launcher
            for (i in 0 until flagsArray.length()) {
                val name = flagsArray.optString(i)
                flagNameMap[name]?.let { flags = flags or it }
            }
            intent.flags = flags
        } else {
            intent.flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }
        try {
            activity?.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(pluginName, intent.component?.packageName + " not found.")
            return intent.component?.packageName + " not found."
        } catch (e: Exception) {
            return intent.component?.packageName + " failed to launch: " + e.message
        }
        if (intent.component?.packageName != null) {
            //return "Launching " + intent.component?.packageName
        } else {
            //return "Launching " + action
        }
        return ""
    }
}
