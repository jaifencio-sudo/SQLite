package com.example.sqlite;

import android.database.sqlite.SQLiteConstraintException;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

// Gestión de Proveedores (soporte a compras / entradas): registrar y listar.
// Accesible para ADMIN y BODEGUERO (cualquier sesión iniciada).
public class ProveedoresActivity extends ProtegidaActivity {

    private ArrayAdapter<String> adapter;
    private final List<String> textos = new ArrayList<>();
    private final List<Proveedor> proveedores = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_proveedores);

        ListView lista = findViewById(R.id.lstProveedores);
        MaterialButton btnNuevo = findViewById(R.id.btnNuevoProveedor);
        MaterialButton btnVolver = findViewById(R.id.btnVolver);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, textos);
        lista.setAdapter(adapter);
        lista.setOnItemClickListener((parent,view,pos,id)->mostrarDetalle(proveedores.get(pos)));

        btnNuevo.setOnClickListener(v -> dialogoProveedor(null));
        btnVolver.setOnClickListener(v -> finish());

        cargar();
    }

    private void cargar() {
        textos.clear();
        proveedores.clear();
        DBHelper helper = new DBHelper(this);
        try {
            for (Proveedor p : helper.listarProveedores()) {
                proveedores.add(p);
                textos.add(p.razonSocial + "\nRUT: " + p.rutEmpresa + "  •  Tel: " + p.numeroContacto);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error al leer: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            helper.close();
        }
        adapter.notifyDataSetChanged();
    }

    // existente == null -> registrar; si no -> editar
    private void dialogoProveedor(Proveedor existente) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_proveedor, null);
        TextInputEditText txtRut = v.findViewById(R.id.dlgRutEmpresa);
        TextInputEditText txtRazon = v.findViewById(R.id.dlgRazonSocial);
        TextInputEditText txtContacto = v.findViewById(R.id.dlgNumeroContacto);
        TextInputEditText txtDireccion = v.findViewById(R.id.dlgDireccion);

        if (existente != null) {
            txtRut.setText(existente.rutEmpresa);
            txtRazon.setText(existente.razonSocial);
            txtContacto.setText(existente.numeroContacto);
            txtDireccion.setText(existente.direccion);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(existente == null ? "Nuevo proveedor" : "Editar proveedor")
                .setView(v)
                .setPositiveButton(existente == null ? "Registrar" : "Guardar", null)   // se sobreescribe abajo
                .setNegativeButton("Cancelar", null)
                .create();

        // Se sobreescribe el click para NO cerrar el diálogo si hay errores de validación
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String rutIngresado = txtRut.getText().toString().trim();
            String razon = txtRazon.getText().toString().trim();
            String contacto = txtContacto.getText().toString().trim();
            String direccion = txtDireccion.getText().toString().trim();

            boolean ok = true;
            String rut = Proveedor.normalizarRut(rutIngresado);
            if (rutIngresado.isEmpty()) {
                txtRut.setError("El RUT de la empresa es obligatorio");
                ok = false;
            } else if (rut == null) {
                txtRut.setError("RUT inválido (ej: 76123456-7)");
                ok = false;
            }
            if (razon.isEmpty()) {
                txtRazon.setError("La razón social es obligatoria");
                ok = false;
            }
            if (contacto.isEmpty()) {
                txtContacto.setError("El número de contacto es obligatorio");
                ok = false;
            } else if (!Proveedor.telefonoValido(contacto)) {
                txtContacto.setError("Número inválido (mínimo 8 dígitos)");
                ok = false;
            }
            if (!ok) return;

            DBHelper helper = new DBHelper(this);
            try {
                if (existente == null) {
                    helper.insertarProveedor(rut, razon, contacto, direccion);
                    Toast.makeText(this, "Proveedor registrado correctamente.", Toast.LENGTH_SHORT).show();
                } else {
                    helper.actualizarProveedor(existente.idProveedor, rut, razon, contacto, direccion);
                    Toast.makeText(this, "Proveedor actualizado.", Toast.LENGTH_SHORT).show();
                }
                dialog.dismiss();
                cargar();
            } catch (SQLiteConstraintException e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                if (msg.contains("UNIQUE")) {
                    txtRut.setError("Ya existe un proveedor con ese RUT");   // rut_empresa duplicado (UK)
                } else {
                    Toast.makeText(this, "Error al guardar: " + msg, Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            } finally {
                helper.close();
            }
        }));
        dialog.show();
    }
    // Ficha del proveedor: datos, últimas entradas recibidas y acciones
    private void mostrarDetalle(Proveedor p) {
        StringBuilder msg = new StringBuilder("RUT: " + p.rutEmpresa +
                "\nTeléfono: " + p.numeroContacto +
                "\nDirección: " + (p.direccion == null || p.direccion.isEmpty() ? "—" : p.direccion) +
                "\n\nÚltimas entradas:");
        DBHelper helper = new DBHelper(this);
        try {
            List<String> entradas = helper.listarEntradasDeProveedor(p.idProveedor, 5);
            if (entradas.isEmpty()) {
                msg.append("\n(sin entradas registradas)");
            } else {
                for (String e : entradas) msg.append("\n").append(e);
            }
        } catch (Exception e) {
            msg.append("\n(no se pudieron leer)");
        } finally {
            helper.close();
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(p.razonSocial)
                .setMessage(msg.toString())
                .setPositiveButton("Registrar entrada", (d, w) -> dialogoEntrada(p))
                .setNeutralButton("Editar", (d, w) -> dialogoProveedor(p))
                .setNegativeButton("Cerrar", null)
                .show();
    }

    // Entrada de bodega asociada al proveedor: producto + cantidad (kg).
    // Usa registrarMovimiento (actualiza stock e historial en una transacción).
    private void dialogoEntrada(Proveedor p) {
        final List<Producto> productos;
        DBHelper h = new DBHelper(this);
        try {
            productos = h.listarProductos(null);
        } finally {
            h.close();
        }
        if (productos.isEmpty()) {
            Toast.makeText(this, "No hay productos registrados. Cree uno primero.", Toast.LENGTH_LONG).show();
            return;
        }
        List<String> nombres = new ArrayList<>();
        for (Producto pr : productos) nombres.add(pr.sku + " · " + pr.nombre);

        Spinner spProducto = new Spinner(this);
        spProducto.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, nombres));

        TextInputEditText txtCantidad = new TextInputEditText(this);
        txtCantidad.setHint("Cantidad recibida (kg)");
        txtCantidad.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

        LinearLayout contenedor = new LinearLayout(this);
        contenedor.setOrientation(LinearLayout.VERTICAL);
        int m = (int) (24 * getResources().getDisplayMetrics().density);
        contenedor.setPadding(m, 8, m, 0);
        contenedor.addView(spProducto);
        contenedor.addView(txtCantidad);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Entrada de " + p.razonSocial)
                .setView(contenedor)
                .setPositiveButton("Registrar", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            double cantidad;
            try {
                cantidad = Double.parseDouble(txtCantidad.getText().toString().trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                txtCantidad.setError("Ingrese una cantidad válida");
                return;
            }
            if (cantidad <= 0) {
                txtCantidad.setError("La cantidad debe ser mayor a 0");
                return;
            }
            Producto elegido = productos.get(spProducto.getSelectedItemPosition());
            DBHelper helper = new DBHelper(this);
            try {
                helper.registrarMovimiento(elegido.idProducto, sesion.getIdUsuario(), p.idProveedor,
                        "ENTRADA", cantidad, "Entrada de proveedor: " + p.razonSocial);
                Toast.makeText(this, "Entrada registrada: " + Producto.formatoKg(cantidad) + " kg de "
                        + elegido.nombre, Toast.LENGTH_LONG).show();
                dialog.dismiss();
            } catch (Exception e) {
                Toast.makeText(this, "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            } finally {
                helper.close();
            }
        }));
        dialog.show();
    }
}