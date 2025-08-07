@tool
extends EditorPlugin

# A class member to hold the editor export plugin during its lifecycle.
var export_plugin : AndroidExportPlugin

func _enter_tree():
	# Initialization of the plugin goes here.
	export_plugin = AndroidExportPlugin.new()
	add_export_plugin(export_plugin)


func _exit_tree():
	# Clean-up of the plugin goes here.
	remove_export_plugin(export_plugin)
	export_plugin = null


class AndroidExportPlugin extends EditorExportPlugin:
	# TODO: Update to your plugin's name.
	var _plugin_name = "PlainLauncherPlugin"

	func _supports_platform(platform):
		if platform is EditorExportPlatformAndroid:
			return true
		return false

	func _get_android_libraries(platform, debug):
		if debug:
			return PackedStringArray([_plugin_name + "/bin/debug/" + _plugin_name + "-debug.aar"])
		else:
			return PackedStringArray([_plugin_name + "/bin/release/" + _plugin_name + "-release.aar"])

	func _get_name():
		return _plugin_name

	# Override to add to manifest
	func _get_android_manifest_application_element_contents(platform: EditorExportPlatform, debug: bool) -> String:
		if not _supports_platform(platform):
			return ""
		
		return """
		<provider
			android:name="androidx.core.content.FileProvider"
			android:authorities="plain.launcher.fileprovider"
			android:exported="false"
			android:grantUriPermissions="true"
			tools:replace="android:authorities">
			<meta-data
				android:name="android.support.FILE_PROVIDER_PATHS"
				android:resource="@xml/plain_launcher_file_paths"
				tools:replace="android:resource" />
		</provider>
		"""

	# Override to add to manifest
	func _get_android_manifest_activity_element_contents(platform, debug):
		if not _supports_platform(platform):
			return ""

		var contents = """
					<intent-filter>\n
						<action android:name=\"android.intent.action.MAIN\" />\n
						<category android:name=\"android.intent.category.LAUNCHER\" />\n
						<category android:name=\"android.intent.category.HOME\" />\n
						<category android:name="android.intent.category.DEFAULT" />\n
						\n

					</intent-filter>\n
		"""
		
		return contents
