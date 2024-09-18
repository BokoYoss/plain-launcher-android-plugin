package org.godotengine.plainlauncher;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import androidx.core.content.FileProvider;

import java.io.File;

public class PlainLauncherFileProvider extends FileProvider {
    public PlainLauncherFileProvider() {
        super(R.xml.file_provider_paths);
    }
}