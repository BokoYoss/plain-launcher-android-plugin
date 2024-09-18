package org.godotengine.plainlauncher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
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
        return signals
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
            Log.i(pluginName, "GOT RESULT FOR " + requestCode + " WITH RETURN CODE " + resultCode + " WITH DATA " + data?.data?.path)

            emitSignal("image_downloaded", data?.data?.path)
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
    private fun launchBrowserForDownload(game: String, system: String) {
        var urlString = "https://www.google.com/search?tbm=isch&q=" + URLEncoder.encode(game + " " + system + " type:png", "utf-8")
        val launcher: Intent? = Intent(Intent.ACTION_VIEW, Uri.parse(urlString))
        launcher?.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        Log.i(pluginName, "Attempting image search request for " + urlString)

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
    private fun openAppSpecificSettings(pkg: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:" + pkg)
        activity?.startActivity(intent)
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
            intent.component = ComponentName(
                componentPackage,
                componentClass
            )
            command += "-n " + componentPackage + componentClass.replace(componentPackage, "/")
        } catch (e: JSONException) {
            Log.w(pluginName, "Missing component path: " + serializedIntent)
        }

        var data = intentMap.optString("data")
        if (data != null && data != "") {
            //Log.d(pluginName, data)
            /**
            val directory = File(Environment.getExternalStorageDirectory(), "internal")
            val path = File(
                directory,
                data.split("/PlainLauncher/".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray().get(1))
            var fileUri = PlainLauncherFileProvider.getUriForFile(activity!!, "org.plain.launcher.fileprovider", path)
            **/
            //var fileUri = FileProvider.getUriForFile(activity!!, "org.plain.launcher.fileprovider", File(data))
            var fileUri = Uri.parse(data)
            Log.i(pluginName, fileUri.path.toString())
            intent.setData(fileUri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            command += " -d \"" + fileUri.path.toString() + "\" "
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
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
        try {
            activity?.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            return intent.component?.packageName + " not found."
        }
        if (intent.component?.packageName != null) {
            return "Launching " + intent.component?.packageName
        } else {
            return "Launching " + action
        }
    }
}
