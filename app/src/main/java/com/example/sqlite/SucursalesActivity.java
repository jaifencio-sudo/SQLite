package com.example.sqlite;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SucursalesActivity extends ProtegidaActivity {

    private DBHelper db;
    private final ExecutorService hilo = Executors.newSingleThreadExecutor();
    private ArrayAdapter<String> adapter;
    private final List<String> textos = new ArrayList<>();
    private final List<Sucursal> sucursales = new ArrayList<>();
    private TextView tvResumen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sucursales);
        db = new DBHelper(this);

        ListView lista = findViewById(R.id.lstSucursales);
        MaterialButton btnNueva = findViewById(R.id.btnNuevaSucursal);
        tvResumen = findViewById(R.id.tvResumenSucursales);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, textos);
        lista.setAdapter(adapter);
        lista.setOnItemClickListener((parent, view, pos, id) -> opciones(sucursales.get(pos)));

        // Solo el ADMIN crea, edita y elimina (igual que la ubicación de la empresa)
        btnNueva.setVisibility(sesion.esAdmin() ? View.VISIBLE : View.GONE);
        btnNueva.setOnClickListener(v -> dialogoSucursal(null));
        findViewById(R.id.btnVolver).setOnClickListener(v -> finish());

        cargar();
    }


    private void cargar() {
        hilo.execute(() -> {
            final List<Sucursal> datos;
            try {
                datos = db.listarSucursales();
            } catch (Exception e) {
                enUi(() -> Toast.makeText(this, "Error al leer: " + e.getMessage(), Toast.LENGTH_LONG).show());
                return;
            }
            enUi(() -> {
                sucursales.clear();
                sucursales.addAll(datos);
                textos.clear();
                for (Sucursal s : datos) {
                    String dir = s.direccion == null || s.direccion.isEmpty() ? "" : s.direccion + "\n";
                    textos.add(s.nombre + "\n" + dir + "Lat: " + s.latitud + "  Lng: " + s.longitud);
                }
                adapter.notifyDataSetChanged();
                tvResumen.setText(datos.size() + (datos.size() == 1 ? " sucursal registrada" : " sucursales registradas"));
            });
        });
    }

    private void opciones(Sucursal s) {
        final List<String> op = new ArrayList<>();
        op.add("Ver en el mapa");
        op.add("Cómo llegar");
        if (sesion.esAdmin()) {
            op.add("Editar");
            op.add("Eliminar");
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(s.nombre)
                .setItems(op.toArray(new String[0]), (d, which) -> {
                    String elegido = op.get(which);
                    if (elegido.equals("Ver en el mapa")) verEnMapa(s);
                    else if (elegido.equals("Cómo llegar")) comoLlegar(s);
                    else if (elegido.equals("Editar")) dialogoSucursal(s);
                    else confirmarEliminar(s);
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }


    private void dialogoSucursal(Sucursal existente) {
        LinearLayout caja = new LinearLayout(this);
        caja.setOrientation(LinearLayout.VERTICAL);
        int m = (int) (24 * getResources().getDisplayMetrics().density);
        caja.setPadding(m, 8, m, 0);
        final TextInputEditText etNombre = campo("Nombre de la sucursal", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS, existente == null ? "" : existente.nombre);
        final TextInputEditText etDireccion = campo("Dirección (opcional)", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS, existente == null ? "" : existente.direccion);
        int tipoNumero = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED;
        final TextInputEditText etLat = campo("Latitud (ej: -29.9027)", tipoNumero,
                existente == null ? "" : String.valueOf(existente.latitud));
        final TextInputEditText etLng = campo("Longitud (ej: -71.2519)", tipoNumero,
                existente == null ? "" : String.valueOf(existente.longitud));
        caja.addView(etNombre);
        caja.addView(etDireccion);
        caja.addView(etLat);
        caja.addView(etLng);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(existente == null ? "Nueva sucursal" : "Editar sucursal")
                .setMessage("En Google Maps: mantén presionado el lugar para ver sus coordenadas.")
                .setView(caja)
                .setPositiveButton(existente == null ? "Registrar" : "Guardar", null)   // se sobreescribe abajo
                .setNegativeButton("Cancelar", null)
                .create();

        // Se sobreescribe el click para NO cerrar el diálogo si hay errores de validación
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            final String nombre = etNombre.getText().toString().trim();
            final String direccion = etDireccion.getText().toString().trim();
            final double lat, lng;
            if (nombre.isEmpty()) {
                etNombre.setError("El nombre es obligatorio");
                return;
            }
            try {
                lat = Double.parseDouble(etLat.getText().toString().trim().replace(',', '.'));
                lng = Double.parseDouble(etLng.getText().toString().trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Latitud y longitud deben ser números.", Toast.LENGTH_LONG).show();
                return;
            }
            if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
                Toast.makeText(this, "Latitud entre -90 y 90, longitud entre -180 y 180.", Toast.LENGTH_LONG).show();
                return;
            }
            hilo.execute(() -> {
                try {
                    if (existente == null) {
                        db.insertarSucursal(nombre, direccion, lat, lng);                    // INSERT
                    } else if (!db.actualizarSucursal(existente.idSucursal, nombre, direccion, lat, lng)) { // UPDATE
                        throw new IllegalArgumentException("La sucursal ya no existe.");
                    }
                    enUi(() -> {
                        dialog.dismiss();
                        Toast.makeText(this, existente == null ? "Sucursal registrada." : "Sucursal actualizada.",
                                Toast.LENGTH_SHORT).show();
                        cargar();
                    });
                } catch (SQLiteConstraintException e) {
                    String msg = e.getMessage() == null ? "" : e.getMessage();
                    enUi(() -> {
                        if (msg.contains("UNIQUE")) etNombre.setError("Ya existe una sucursal con ese nombre");
                        else Toast.makeText(this, "Datos no válidos: " + msg, Toast.LENGTH_LONG).show();
                    });
                } catch (Exception e) {
                    enUi(() -> Toast.makeText(this, "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
        }));
        dialog.show();
    }


    private void confirmarEliminar(Sucursal s) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Eliminar sucursal")
                .setMessage("¿Eliminar \"" + s.nombre + "\"? Esta acción no se puede deshacer.")
                .setPositiveButton("Eliminar", (d, w) -> hilo.execute(() -> {
                    try {
                        final boolean ok = db.eliminarSucursal(s.idSucursal);               // DELETE
                        enUi(() -> {
                            Toast.makeText(this, ok ? "Sucursal eliminada." : "La sucursal ya no existe.",
                                    Toast.LENGTH_SHORT).show();
                            cargar();
                        });
                    } catch (Exception e) {
                        enUi(() -> Toast.makeText(this, "Error al eliminar: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }))
                .setNegativeButton("Cancelar", null)
                .show();
    }


    private void verEnMapa(Sucursal s) {
        Uri uri = Uri.parse("geo:" + s.latitud + "," + s.longitud + "?q=" + s.latitud + "," + s.longitud
                + "(" + Uri.encode(s.nombre) + ")");
        abrir(new Intent(Intent.ACTION_VIEW, uri));
    }


    private void comoLlegar(Sucursal s) {
        Uri uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + s.latitud + ","
                + s.longitud + "&travelmode=driving");
        abrir(new Intent(Intent.ACTION_VIEW, uri));
    }


    private void abrir(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Google Maps no está disponible en este dispositivo", Toast.LENGTH_LONG).show();
        }
    }

    private TextInputEditText campo(String hint, int tipo, String valor) {
        TextInputEditText e = new TextInputEditText(this);
        e.setHint(hint);
        e.setInputType(tipo);
        e.setSingleLine(true);
        e.setText(valor);
        return e;
    }


    private void enUi(Runnable r) {
        runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) r.run(); });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hilo.execute(db::close);   // cierra la BD cuando termine lo pendiente
        hilo.shutdown();
    }
}
