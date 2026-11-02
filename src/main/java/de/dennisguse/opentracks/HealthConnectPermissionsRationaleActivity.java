package de.dennisguse.opentracks;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import de.dennisguse.opentracks.databinding.ActivityHealthConnectPermissionsRationaleBinding;

public class HealthConnectPermissionsRationaleActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityHealthConnectPermissionsRationaleBinding viewBinding = ActivityHealthConnectPermissionsRationaleBinding.inflate(getLayoutInflater());
        setSupportActionBar(viewBinding.healthConnectActivityToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setContentView(viewBinding.getRoot());
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}
