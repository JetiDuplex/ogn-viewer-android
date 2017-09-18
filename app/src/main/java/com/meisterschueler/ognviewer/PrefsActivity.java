package com.meisterschueler.ognviewer;

import android.app.Activity;
import android.os.Bundle;

import com.meisterschueler.ognviewer.ui.PrefsFragment;

public class PrefsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new PrefsFragment())
                .commit();
    }
}
