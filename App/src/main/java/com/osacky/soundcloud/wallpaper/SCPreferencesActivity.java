package com.osacky.soundcloud.wallpaper;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class SCPreferencesActivity extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.prefs);
    }
}
