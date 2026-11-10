package com.example.sqlite;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CodigosBarrasActivity extends ProtegidaActivity {

    private static final String[] FILTROS = {"TODOS", "CON", "SIN"};
    private static final String[] ROTULOS = {"Todos", "Con código", "Sin código"};

    private DBHelper db;
    private final ExecutorService hilo = Executors.newSingleThreadExecutor();
    private ArrayAdapter<String> adapter;
    private final List<String> textos = new ArrayList<>();
    private final List<Producto> productos = new ArrayList<>();
    private TextInputEditText txtBuscar;
    private MaterialButton btnFiltro;
    private TextView tvResumen;
    private int filtro = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_codigos_barras);
        db = new DBHelper(this);

        ListView lista = findViewById(R.id.lstCodigos);
        txtBuscar = findViewById(R.id.txtBuscarCodigo);
        btnFiltro = findViewById(R.id.btnFiltroCodigo);
        tvResumen = findViewById(R.id.tvResumenCodigos);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, textos);
        lista.setAdapter(adapter);
        lista.setOnItemClickListener((parent, view, pos, id) -> opciones(productos.get(pos)));

        txtBuscar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { cargar(); }
        });
        // El botón rota el filtro: Todos -> Con código -> Sin código
        btnFiltro.setOnClickListener(v -> {
            filtro = (filtro + 1) % FILTROS.length;
            btnFiltro.setText("Mostrar: " + ROTULOS[filtro]);
            cargar();
        });
        findViewById(R.id.btnVolver).setOnClickListener(v -> finish());

        cargar();
    }

    private void cargar() {
        final String texto = txtBuscar.getText() == null ? "" : txtBuscar.getText().toString().trim();
        final String estado = FILTROS[filtro];
        hilo.execute(() -> {
            final List<Producto> datos;
            try {
                datos = db.listarProductosConBarras(texto, estado);
            } catch (Exception e) {
                enUi(() -> Toast.makeText(this, "Error al leer: " + e.getMessage(), Toast.LENGTH_LONG).show());
                return;
            }
            enUi(() -> {
                productos.clear();
                productos.addAll(datos);
                textos.clear();
                int conCodigo = 0;
                for (Producto p : datos) {
                    boolean tiene = tieneCodigo(p);
                    if (tiene) conCodigo++;
                    textos.add(p.nombre + "\nSKU: " + p.sku + "  •  " + (tiene ? p.codigoBarras : "Sin código"));
                }
                adapter.notifyDataSetChanged();
                tvResumen.setText(datos.size() + " productos (" + conCodigo + " con código)");
            });
        });
    }

    private boolean tieneCodigo(Producto p) {
        return p.codigoBarras != null && !p.codigoBarras.isEmpty();
    }

    private void opciones(Producto p) {
        final boolean tiene = tieneCodigo(p);
        final List<String> op = new ArrayList<>();
        op.add(tiene ? "Cambiar código (escribir)" : "Asignar código (escribir)");
        op.add(tiene ? "Cambiar código (escanear)" : "Asignar código (escanear)");
        if (tiene) op.add("Quitar código");
        new MaterialAlertDialogBuilder(this)
                .setTitle(p.nombre)
                .setItems(op.toArray(new String[0]), (d, which) -> {
                    if (which == 0) dialogoEscribir(p);
                    else if (which == 1) escanear(p);
                    else confirmarQuitar(p);
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    /** Entrada de datos por teclado. */
    private void dialogoEscribir(Producto p) {
        final TextInputEditText et = new TextInputEditText(this);
        et.setHint("Código de barras");
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setSingleLine(true);
        if (tieneCodigo(p)) et.setText(p.codigoBarras);
        int m = (int) (24 * getResources().getDisplayMetrics().density);
        FrameLayout caja = new FrameLayout(this);
        caja.setPadding(m, 8, m, 0);
        caja.addView(et);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(p.nombre)
                .setView(caja)
                .setPositiveButton("Guardar", null)   // se sobreescribe abajo
                .setNegativeButton("Cancelar", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String codigo = et.getText().toString().trim();
            if (codigo.isEmpty()) {
                et.setError("Ingrese un código");
                return;
            }
            if (codigo.length() > 64) {
                et.setError("Máximo 64 caracteres");
                return;
            }
            dialog.dismiss();
            guardar(p, codigo);
        }));
        dialog.show();
    }


    private void escanear(Producto p) {
        GmsBarcodeScannerOptions opciones = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A, Barcode.FORMAT_CODE_128, Barcode.FORMAT_QR_CODE)
                .build();
        GmsBarcodeScanning.getClient(this, opciones).startScan()
                .addOnSuccessListener(b -> {
                    String valor = b.getRawValue();
                    if (valor == null || valor.trim().isEmpty())
                        Toast.makeText(this, "No se pudo leer el código. Intente de nuevo.", Toast.LENGTH_LONG).show();
                    else guardar(p, valor.trim());
                })
                .addOnCanceledListener(() ->
                        Toast.makeText(this, "Escaneo cancelado", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "No se pudo usar la cámara: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }


    private void guardar(Producto p, String codigo) {
        hilo.execute(() -> {
            final int r;
            try {
                r = db.actualizarBarras(p.idProducto, codigo);
            } catch (Exception e) {
                enUi(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
                return;
            }
            enUi(() -> {
                if (r == DBHelper.BARRAS_OK) {
                    Toast.makeText(this, "Código guardado", Toast.LENGTH_SHORT).show();
                    cargar();
                } else if (r == DBHelper.BARRAS_DUPLICADO) {
                    Toast.makeText(this, "Ese código ya pertenece a otro producto", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "El producto ya no está disponible", Toast.LENGTH_LONG).show();
                    cargar();
                }
            });
        });
    }


    private void confirmarQuitar(Producto p) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Quitar código")
                .setMessage("¿Quitar el código " + p.codigoBarras + " de \"" + p.nombre
                        + "\"? El producto y su stock no se modifican.")
                .setPositiveButton("Quitar", (d, w) -> hilo.execute(() -> {
                    try {
                        final boolean ok = db.quitarBarras(p.idProducto);
                        enUi(() -> {
                            Toast.makeText(this, ok ? "Código quitado" : "El producto no tenía código",
                                    Toast.LENGTH_SHORT).show();
                            cargar();
                        });
                    } catch (Exception e) {
                        enUi(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }))
                .setNegativeButton("Cancelar", null)
                .show();
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
