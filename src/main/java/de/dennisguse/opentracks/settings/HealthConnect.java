package de.dennisguse.opentracks.settings;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.PermissionController;

import de.dennisguse.opentracks.io.healthconnect.exporter.HealthConnectUtils;

public class HealthConnect {

    public static class Settings extends AppCompatActivity {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            startActivity(new Intent(HealthConnectClient.getHealthConnectSettingsAction()));

            finish();
        }
    }

    public static class ConfigurePermissions extends AppCompatActivity {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (!HealthConnectUtils.isAvailable(this)) {
                finish();
            }

            registerForActivityResult(
                    PermissionController.createRequestPermissionResultContract(),
                    grantedPermissions -> {
                        // TODO User feedback via Toast.makeText()?
                        finish();
                    }
            ).launch(HealthConnectUtils.PERMISSIONS);
        }
    }

    public static class ExportAll extends AppCompatActivity {
        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            HealthConnectUtils.postExportAll(this);

            finish();
        }
    }
}
