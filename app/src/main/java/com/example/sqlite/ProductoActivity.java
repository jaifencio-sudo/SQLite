package com.example.sqlite;

import android.database.sqlite.SQLiteConstraintException;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import java.util.List;

// Formulario de producto: crea uno nuevo o edita/elimina uno existente (EXTRA_ID)
public class ProductoActivity extends ProtegidaActivity {

    public static final String EXTRA_ID = "id_producto";
    // Opcional: código de barras ya leído (lo envía EscanerActivity al crear un producto desde un código no registrado)
    public static final String EXTRA_BARRAS = "codigo_barras";

    private TextInputEditText txtSku, txtDescripcion, txtNombre, txtStock, txtStockMin, txtPrecio, txtBarras;
    private MaterialAutoCompleteTextView cmbCategoria;
    private MaterialButton btnEliminar;
    private long idProducto = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_producto);

        TextView titulo = findViewById(R.id.lblTitulo);
        txtSku = findViewById(R.id.txtSku);
        txtDescripcion = findViewById(R.id.txtDescripcion);
        txtNombre = findViewById(R.id.txtNombre);
        cmbCategoria = findViewById(R.id.cmbCategoria);
        txtStock = findViewById(R.id.txtStock);
        txtStockMin = findViewById(R.id.txtStockMin);
        txtPrecio = findViewById(R.id.txtPrecio);
        txtBarras = findViewById(R.id.txtBarras);
        findViewById(R.id.btnEscanearBarras).setOnClickListener(v -> escanearBarras());
        MaterialButton btnGuardar = findViewById(R.id.btnGuardar);
        btnEliminar = findViewById(R.id.btnEliminar);
        MaterialButton btnVolver = findViewById(R.id.btnVolver);

        // RF-10: el desplegable lista las categorías ACTIVAS que están en la base de datos
        List<String> nombresCategorias;
        DBHelper hCat = new DBHelper(this);
        try {
            nombresCategorias = hCat.listarNombresCategoriasActivas();
        } finally {
            hCat.close();
        }
        cmbCategoria.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, nombresCategorias));
        if (!nombresCategorias.isEmpty()) cmbCategoria.setText(nombresCategorias.get(0), false);

        idProducto = getIntent().getLongExtra(EXTRA_ID, -1);
        btnEliminar.setVisibility(View.GONE);

        if (idProducto != -1) {
            titulo.setText("Editar producto");
            cargarProducto();
            // Solo el ADMIN puede eliminar productos
            if (sesion.esAdmin()) btnEliminar.setVisibility(View.VISIBLE);
        }

        // Producto nuevo desde el escáner: el código llega ya escrito
        String barrasRecibido = getIntent().getStringExtra(EXTRA_BARRAS);
        if (idProducto == -1 && barrasRecibido != null) txtBarras.setText(barrasRecibido);

        btnGuardar.setOnClickListener(v -> guardar());
        btnEliminar.setOnClickListener(v -> confirmarEliminar());
        btnVolver.setOnClickListener(v -> finish());
    }

    private void cargarProducto() {
        DBHelper helper = new DBHelper(this);
        try {
            Producto p = helper.obtenerProducto(idProducto);
            if (p == null) {
                Toast.makeText(this, "El producto ya no existe.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            txtSku.setText(p.sku);
            txtDescripcion.setText(p.descripcion);
            txtStock.setEnabled(false);
            txtNombre.setText(p.nombre);
            cmbCategoria.setText(p.categoria, false);
            txtStock.setText(Producto.formatoKg(p.stock));
            txtStockMin.setText(Producto.formatoKg(p.stockMinimo));
            txtPrecio.setText(String.valueOf(p.precioKg));
            txtBarras.setText(p.codigoBarras);
        } finally {
            helper.close();
        }
    }

    private void guardar() {
        String nombre = txtNombre.getText().toString().trim();
        String categoria = cmbCategoria.getText().toString().trim();
        String stockStr = txtStock.getText().toString().trim().replace(',', '.');
        String minStr = txtStockMin.getText().toString().trim().replace(',', '.');
        String precioStr = txtPrecio.getText().toString().trim();

        // RF-10: el desplegable es solo de selección (inputType none), así que lo único que falta validar es que haya una elegida
        if (categoria.isEmpty()) {
            Toast.makeText(this, "Seleccione una categoría. Si no hay ninguna, registre una en Categorías.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (nombre.isEmpty() || stockStr.isEmpty()
                || minStr.isEmpty() || precioStr.isEmpty()) {
            Toast.makeText(this, "Complete todos los campos.", Toast.LENGTH_SHORT).show();
            return;
        }

        Producto p = new Producto();
        try {
            p.sku = txtSku.getText().toString().trim();
            p.descripcion = txtDescripcion.getText().toString().trim();
            p.nombre = nombre;
            p.codigoBarras = txtBarras.getText().toString().trim();
            p.categoria = categoria;
            p.stock = Double.parseDouble(stockStr);
            p.stockMinimo = Double.parseDouble(minStr);
            p.precioKg = Integer.parseInt(precioStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Stock, mínimo y precio deben ser números válidos.", Toast.LENGTH_LONG).show();
            return;
        }
        if (p.stock < 0 || p.stockMinimo < 0 || p.precioKg < 0) {
            Toast.makeText(this, "Los valores no pueden ser negativos.", Toast.LENGTH_SHORT).show();
            return;
        }

        DBHelper helper = new DBHelper(this);
        try {
            if (idProducto == -1) {
                helper.insertarProducto(p);
                Toast.makeText(this, "Producto guardado.", Toast.LENGTH_SHORT).show();
            } else {
                p.idProducto = idProducto;
                helper.actualizarProducto(p);
                Toast.makeText(this, "Producto actualizado.", Toast.LENGTH_SHORT).show();
            }
            finish();
        } catch (SQLiteConstraintException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("codigo_barras")) {
                txtBarras.setError("Ese código de barras ya pertenece a otro producto");
            } else {
                Toast.makeText(this, "SKU duplicado o datos que no cumplen las restricciones.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            helper.close();
        }
    }

    /** Lee un código con la cámara (Google Code Scanner, sin permiso CAMERA) y lo escribe en el campo. */
    private void escanearBarras() {
        GmsBarcodeScannerOptions opciones = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A, Barcode.FORMAT_CODE_128, Barcode.FORMAT_QR_CODE)
                .build();
        GmsBarcodeScanning.getClient(this, opciones).startScan()
                .addOnSuccessListener(b -> {
                    if (b.getRawValue() != null) txtBarras.setText(b.getRawValue().trim());
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                        "No se pudo usar la cámara. Escriba el código a mano.", Toast.LENGTH_LONG).show());
    }

    private void confirmarEliminar() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Deshabilitar producto")
                .setMessage("Se ocultará del catálogo y se conservará su historial. ¿Continuar?")
                .setPositiveButton("Deshabilitar", (d, w) -> eliminar())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void eliminar() {
        DBHelper helper = new DBHelper(this);
        try {
            if (helper.eliminarProducto(idProducto)) {
                Toast.makeText(this, "Producto deshabilitado.", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "No se encontró el producto.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error al eliminar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            helper.close();
        }
    }
}
