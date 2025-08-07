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
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder


object RequestCodes {
    const val REQUEST_SET_STORAGE = 1
    const val REQUEST_GET_DOWNLOADED_IMAGE = 2
    const val REQUEST_PERMISSIONS = 3
    const val LAUNCH_APPLICATION = 4
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

            emitSignal("configure_storage_location", data?.data?.path)
        } else if (requestCode == RequestCodes.REQUEST_GET_DOWNLOADED_IMAGE) {
            Log.i(
                pluginName,
                "GOT RESULT FOR " + requestCode + " WITH RETURN CODE " + resultCode + " WITH DATA " + data?.data?.path
            )

            emitSignal("image_downloaded", data?.data?.path)
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
                if (volume.isRemovable) {
                    return volume.directory.toString()
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
    private fun launchBrowserForDownload(game: String, system: String, source: String) {
        var urlString = "https://www.google.com/search?tbm=isch&q=" + URLEncoder.encode(game + " " + system + " type:png", "utf-8")

        if (source.lowercase().equals("tgdb")) {
            urlString = "https://thegamesdb.net/search.php?name=" + URLEncoder.encode(game, "utf-8")
        }
        else if (source.lowercase().equals("duckduckgo")) {
            urlString = "https://duckduckgo.com/?t=h_&iax=images&ia=images&q=" + URLEncoder.encode(game, "utf-8")
        }
        else if (source.lowercase().equals("launchbox")) {
            urlString = "https://gamesdb.launchbox-app.com/games/results/" + URLEncoder.encode(game, "utf-8").replace("+", "%20")
        }
        else if (source.lowercase().equals("steamgriddb")) {
            urlString = "https://www.steamgriddb.com/search/grids?term=" + URLEncoder.encode(game, "utf-8")
        }
        val launcher: Intent? = Intent(Intent.ACTION_VIEW, Uri.parse(urlString))
        launcher?.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        Log.i(pluginName, "Attempting image search request for " + source + ": " + urlString)

        if (launcher != null) {
            activity?.startActivity(launcher)
        }
    }

    @UsedByGodot
    private fun launchPackage(pkg: String) {
        val launcher: Intent? = activity?.packageManager?.getLaunchIntentForPackage(pkg)

        if (launcher != null) {
            activity?.startActivity(launcher)
        }
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
        val results = JSONObject()
        for (appInfo: ApplicationInfo in rawAppList!!) {
            if (activity?.packageManager?.getLaunchIntentForPackage(appInfo.packageName) == null) {
                continue
            }
            results.put(appInfo.loadLabel(packageManager).toString(), appInfo.packageName)
        }
        return results.toString()
    }

    @UsedByGodot
    private fun launchDefaultApp(category: String): String {
        val intent: Intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category)
        try {
            activity?.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            return "NOT_FOUND"
        }
        return "SUCCESS"
    }

    @UsedByGodot
    private fun openAppSpecificSettings(pkg: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:" + pkg)
        activity?.startActivity(intent)
    }

    /**
     * Example showing how to declare a method that's used by Godot.
     *
     * Shows a 'Hello World' toast.
     */
    @UsedByGodot
    private fun launchIntent(serializedIntent: String): String? {
        val intentMap: JSONObject = JSONObject(serializedIntent)

        var packageName = intentMap.optString("package")
        if (packageName != null && packageName != "") {
            launchPackage(packageName)
            return "Launching " + packageName
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

        var data = intentMap.optString("data")
        if (data != null && data != "") {
            //Log.i(pluginName, "Created file provider uri: " + uri.toString())
            intent.setData(Uri.parse(data))
            command += " -d \"" + data + "\" "
        }

        var providedFile = intentMap.optString("providedFile")
        if (providedFile != null && providedFile != "") {
            var dataFile = File(providedFile)
            try {
                var uri: Uri = FileProvider.getUriForFile(
                    activity,
                    "plain.launcher.fileprovider",
                    dataFile
                )
                intent.setData(uri)
                Log.i(pluginName, "Created file provider uri: " + uri.path)
                command += " -d \"" + uri.toString() + "\" "
            } catch (e: Exception) {
                Log.e(pluginName, "Failed to make file provider: " + e.toString(), e)
            }
        }

        var category = intentMap.optString("category")
        if (category != null && category != "") {
            Log.d(pluginName, category)
            intent.addCategory(category)
            command += " -c \"" + category + "\" "
        }

        try {
            // TODO: Why doesn't optJSONObject and null check work here?
            val extraMap: JSONObject = intentMap.getJSONObject("extras")
            if (extraMap != null) {
                val extraKeys: Iterator<String> = extraMap.keys()
                extraKeys.forEach { extraKey ->
                    var extraValue: String = extraMap.getString(extraKey)
                    intent.putExtra(extraKey, extraMap.getString(extraKey))
                    command += " -e " + extraKey + " \"" + extraValue + "\" "
                }
            }
        } catch (e: JSONException) {
            // don't worry if we don't have extras
            Log.w(pluginName, "Skipping extras for " + intent.component?.packageName)
        }

        command += "  --activity-clear-task --activity-clear-top --activity-no-history"
        Log.i(pluginName, command)

        intent.flags =
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
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
