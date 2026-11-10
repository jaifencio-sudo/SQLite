package com.example.sqlite;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UbicacionActivity extends ProtegidaActivity {

    private DBHelper db;
    private final ExecutorService hilo = Executors.newSingleThreadExecutor();
    private TextView tvEmpresa, tvMiUbicacion;
    private MaterialButton btnEditar;
    private String nombre;
    private double latEmpresa, lngEmpresa;
    private boolean empresaLista = false;
    private Runnable accionPendiente;   // lo que se hará cuando el usuario conceda el permiso
    private FusedLocationProviderClient gps;


    private final ActivityResultLauncher<String[]> pedirPermiso =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> {
                boolean ok = Boolean.TRUE.equals(r.get(Manifest.permission.ACCESS_FINE_LOCATION))
                        || Boolean.TRUE.equals(r.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (ok && accionPendiente != null) accionPendiente.run();
                else tvMiUbicacion.setText("Permiso de ubicación denegado. La ubicación de la empresa sigue disponible.");
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ubicacion);
        db = new DBHelper(this);
        gps = LocationServices.getFusedLocationProviderClient(this);
        tvEmpresa = findViewById(R.id.tvEmpresa);
        tvMiUbicacion = findViewById(R.id.tvMiUbicacion);
        btnEditar = findViewById(R.id.btnEditarEmpresa);

        findViewById(R.id.btnVerEmpresa).setOnClickListener(v -> verEmpresa());
        findViewById(R.id.btnMiUbicacion).setOnClickListener(v -> conPermiso(this::obtenerMiUbicacion));
        findViewById(R.id.btnComoLlegar).setOnClickListener(v -> conPermiso(this::comoLlegar));
        findViewById(R.id.btnVolver).setOnClickListener(v -> finish());

        // Solo el ADMIN configura las coordenadas de la empresa
        btnEditar.setVisibility(sesion.esAdmin() ? View.VISIBLE : View.GONE);
        btnEditar.setOnClickListener(v -> dialogoEditar());

        cargarEmpresa();
    }

    /** Lee la empresa desde SQLite en un hilo de fondo. */
    private void cargarEmpresa() {
        hilo.execute(() -> {
            Empresa e = null;
            try { e = db.obtenerEmpresa(); } catch (Exception ex) { /* se informa como "no configurada" */ }
            final Empresa emp = e;
            enUi(() -> {
                if (emp == null) {   // flujo alterno: ubicación no configurada
                    empresaLista = false;
                    tvEmpresa.setText("Ubicación de la empresa no configurada.");
                    return;
                }
                nombre = emp.nombre;
                latEmpresa = emp.latitud;
                lngEmpresa = emp.longitud;
                empresaLista = true;
                tvEmpresa.setText(nombre + "\nLat: " + latEmpresa + "  Lng: " + lngEmpresa);
            });
        });
    }

    /** RF-24: abre Google Maps con un marcador en la empresa. No necesita permiso GPS. */
    private void verEmpresa() {
        if (!empresaLista) { Toast.makeText(this, "Ubicación no configurada", Toast.LENGTH_SHORT).show(); return; }
        Uri uri = Uri.parse("geo:" + latEmpresa + "," + lngEmpresa + "?q=" +
                latEmpresa + "," + lngEmpresa + "(" + Uri.encode(nombre) + ")");
        abrir(new Intent(Intent.ACTION_VIEW, uri));
    }

    /** RF-26: ruta desde la ubicación actual hasta la empresa. */
    private void comoLlegar() {
        if (!empresaLista) { Toast.makeText(this, "Ubicación no configurada", Toast.LENGTH_SHORT).show(); return; }
        if (!ubicacionActivada()) {   // flujo alterno: GPS desactivado
            Toast.makeText(this, "Active la ubicación para calcular la ruta", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return;
        }
        Uri uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" +
                latEmpresa + "," + lngEmpresa + "&travelmode=driving");
        abrir(new Intent(Intent.ACTION_VIEW, uri));
    }

    /** RF-25: coordenadas del dispositivo + distancia a la empresa. */
    private void obtenerMiUbicacion() {
        if (!ubicacionActivada()) {
            tvMiUbicacion.setText("Ubicación desactivada. Actívela para obtener su posición.");
            return;
        }
        tvMiUbicacion.setText("Obteniendo ubicación…");
        try {
            gps.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(loc -> {
                        if (loc == null) { tvMiUbicacion.setText("No se pudo obtener la ubicación."); return; }
                        String t = "Tu ubicación\nLat: " + loc.getLatitude() + "  Lng: " + loc.getLongitude();
                        if (empresaLista) {
                            float[] d = new float[1];
                            Location.distanceBetween(loc.getLatitude(), loc.getLongitude(), latEmpresa, lngEmpresa, d);
                            t += String.format(Locale.forLanguageTag("es-CL"), "\nDistancia a la empresa: %.1f km", d[0] / 1000f);
                        }
                        tvMiUbicacion.setText(t);
                    })
                    .addOnFailureListener(e -> tvMiUbicacion.setText("Error de ubicación: " + e.getMessage()));
        } catch (SecurityException se) {
            tvMiUbicacion.setText("Falta el permiso de ubicación.");
        }
    }

    /** ADMIN: cambia nombre y coordenadas de la empresa (UPDATE en SQLite, validado por CHECK). */
    private void dialogoEditar() {
        LinearLayout caja = new LinearLayout(this);
        caja.setOrientation(LinearLayout.VERTICAL);
        int m = (int) (24 * getResources().getDisplayMetrics().density);
        caja.setPadding(m, 8, m, 0);
        TextInputEditText etNombre = campo("Nombre", InputType.TYPE_CLASS_TEXT, empresaLista ? nombre : "");
        TextInputEditText etLat = campo("Latitud (ej: -29.9027)",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED,
                empresaLista ? String.valueOf(latEmpresa) : "");
        TextInputEditText etLng = campo("Longitud (ej: -71.2519)",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED,
                empresaLista ? String.valueOf(lngEmpresa) : "");
        caja.addView(etNombre); caja.addView(etLat); caja.addView(etLng);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Ubicación de la empresa")
                .setMessage("En Google Maps: clic derecho sobre el lugar → copiar coordenadas.")
                .setView(caja)
                .setPositiveButton("Guardar", null)
                .setNegativeButton("Cancelar", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(b -> {
                    final String n = etNombre.getText().toString().trim();
                    final double lat, lng;
                    try {
                        lat = Double.parseDouble(etLat.getText().toString().trim().replace(',', '.'));
                        lng = Double.parseDouble(etLng.getText().toString().trim().replace(',', '.'));
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Latitud y longitud deben ser números.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (n.isEmpty() || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
                        Toast.makeText(this, "Nombre obligatorio, latitud -90..90 y longitud -180..180.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    dialog.dismiss();
                    hilo.execute(() -> {
                        try {
                            db.actualizarEmpresa(n, lat, lng);
                            enUi(() -> { Toast.makeText(this, "Ubicación guardada", Toast.LENGTH_SHORT).show(); cargarEmpresa(); });
                        } catch (Exception e) {
                            enUi(() -> Toast.makeText(this, "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    });
                }));
        dialog.show();
    }

    private TextInputEditText campo(String hint, int tipo, String valor) {
        TextInputEditText e = new TextInputEditText(this);
        e.setHint(hint); e.setInputType(tipo); e.setSingleLine(true); e.setText(valor);
        return e;
    }

    /** Ejecuta la acción solo si hay permiso; si no, lo solicita (RF-25: "solo con autorización"). */
    private void conPermiso(Runnable accion) {
        boolean fino = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean aprox = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (fino || aprox) {
            accion.run();
        } else {
            accionPendiente = accion;
            pedirPermiso.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    private boolean ubicacionActivada() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        return lm != null && lm.isLocationEnabled();   // API 28+ (minSdk del proyecto: 30)
    }

    /** Flujo alterno: Google Maps no disponible -> se informa. */
    private void abrir(Intent i) {
        try { startActivity(i); }
        catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Google Maps no está disponible en este dispositivo", Toast.LENGTH_LONG).show();
        }
    }

    private void enUi(Runnable r) {
        runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) r.run(); });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hilo.execute(db::close);
        hilo.shutdown();
    }
}
