package com.example.sqlite;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EscanerActivity extends ProtegidaActivity {

    private DBHelper db;
    private final ExecutorService hilo = Executors.newSingleThreadExecutor();
    private TextView tvResultado;
    private MaterialButton btnEntrada, btnSalida, btnAsignar, btnCrear;
    private TextInputEditText etManual;
    private String codigoActual, skuActual;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_escaner);
        db = new DBHelper(this);

        tvResultado = findViewById(R.id.tvResultado);
        etManual = findViewById(R.id.etManual);
        btnEntrada = findViewById(R.id.btnEntrada);
        btnSalida = findViewById(R.id.btnSalida);
        btnAsignar = findViewById(R.id.btnAsignar);
        btnCrear = findViewById(R.id.btnCrear);

        findViewById(R.id.btnEscanear).setOnClickListener(v -> escanear());
        // Flujo alternativo de CU-07: búsqueda manual si la cámara no está disponible
        findViewById(R.id.btnBuscarManual).setOnClickListener(v -> {
            String cod = etManual.getText().toString().trim();
            if (cod.isEmpty()) { etManual.setError("Ingrese un código"); return; }
            consultar(cod);
        });
        btnEntrada.setOnClickListener(v -> irAMovimiento("ENTRADA"));
        btnSalida.setOnClickListener(v -> irAMovimiento("SALIDA"));
        btnAsignar.setOnClickListener(v -> dialogoAsignar());

        btnCrear.setOnClickListener(v -> {
            Intent i = new Intent(this, ProductoActivity.class);
            i.putExtra(ProductoActivity.EXTRA_BARRAS, codigoActual);
            startActivity(i);
        });
        findViewById(R.id.btnVolver).setOnClickListener(v -> finish());
        ocultarAcciones();
    }

    /** Abre la cámara y lee el código. */
    private void escanear() {
        GmsBarcodeScannerOptions opciones = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A, Barcode.FORMAT_CODE_128, Barcode.FORMAT_QR_CODE)
                .build();
        GmsBarcodeScanning.getClient(this, opciones).startScan()
                .addOnSuccessListener(b -> {
                    String valor = b.getRawValue();
                    if (valor == null || valor.trim().isEmpty())
                        tvResultado.setText("No se pudo leer el código. Intente de nuevo o búsquelo manualmente.");
                    else consultar(valor.trim());
                })
                .addOnCanceledListener(() ->
                        Toast.makeText(this, "Escaneo cancelado", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        tvResultado.setText("No se pudo usar la cámara. Use la búsqueda manual.\n" + e.getMessage()));
    }


    private void consultar(String codigo) {
        codigoActual = codigo;
        hilo.execute(() -> {
            final Producto prod;
            try {
                prod = db.buscarProductoPorBarras(codigo);   // <-- aquí se usa SQLite
            } catch (Exception e) {
                enUi(() -> tvResultado.setText("Error de base de datos: " + e.getMessage()));
                return;
            }
            enUi(() -> mostrar(codigo, prod));
        });
    }

    private void mostrar(String codigo, Producto p) {
        ocultarAcciones();
        if (p == null) {
            tvResultado.setText("Producto no encontrado\nCódigo: " + codigo);
            btnAsignar.setVisibility(View.VISIBLE);
            btnCrear.setVisibility(View.VISIBLE);
            return;
        }
        skuActual = p.sku;
        if (!p.activo) {
            tvResultado.setText("El producto " + p.nombre + " está deshabilitado.");
            return;
        }
        // El stock se guarda en gramos en SQLite; Producto ya lo entrega en kg.
        String estado = p.stock == 0 ? "SIN STOCK" : (p.stockBajo() ? "STOCK BAJO" : "NORMAL");
        tvResultado.setText("SKU: " + p.sku + "\nNombre: " + p.nombre + "\nCategoría: " + p.categoria
                + "\nStock actual: " + Producto.formatoKg(p.stock) + " kg"
                + "\nStock mínimo: " + Producto.formatoKg(p.stockMinimo) + " kg"
                + "\nEstado: " + estado);
        btnEntrada.setVisibility(View.VISIBLE);
        btnSalida.setVisibility(View.VISIBLE);
    }


    private void irAMovimiento(String tipo) {
        Intent i = new Intent(this, MovimientosActivity.class);
        i.putExtra(MovimientosActivity.EXTRA_SKU, skuActual);
        i.putExtra(MovimientosActivity.EXTRA_TIPO, tipo);
        startActivity(i);
    }


    private void dialogoAsignar() {
        final TextInputEditText et = new TextInputEditText(this);
        et.setHint("SKU del producto");
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setSingleLine(true);
        int m = (int) (24 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout caja = new android.widget.FrameLayout(this);
        caja.setPadding(m, 8, m, 0);
        caja.addView(et);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Asociar código " + codigoActual)
                .setView(caja)
                .setPositiveButton("Guardar", (d, w) -> {
                    final String sku = et.getText().toString().trim();
                    final String codigo = codigoActual;
                    hilo.execute(() -> {
                        int r;
                        try {
                            r = db.asignarBarrasPorSku(sku, codigo);
                        } catch (Exception e) {
                            enUi(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
                            return;
                        }
                        final int res = r;
                        enUi(() -> {
                            if (res == DBHelper.BARRAS_OK) {
                                Toast.makeText(this, "Código asociado", Toast.LENGTH_SHORT).show();
                                consultar(codigo);
                            } else if (res == DBHelper.BARRAS_SKU_INEXISTENTE) {
                                Toast.makeText(this, "Ese SKU no existe o está deshabilitado", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(this, "Ese código ya pertenece a otro producto", Toast.LENGTH_LONG).show();
                            }
                        });
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void ocultarAcciones() {
        btnEntrada.setVisibility(View.GONE);
        btnSalida.setVisibility(View.GONE);
        btnAsignar.setVisibility(View.GONE);
        btnCrear.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (codigoActual != null) consultar(codigoActual);
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
