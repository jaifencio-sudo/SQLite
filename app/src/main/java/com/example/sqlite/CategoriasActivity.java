package com.example.sqlite;

import android.database.sqlite.SQLiteConstraintException;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

// CU-09 / RF-09: Registrar categorías de productos.
// Al tocar una categoría se puede editar (p. ej. agregar la descripción que le faltaba).
// RF-10: listar categorías. Accesible para ADMIN y BODEGUERO (cualquier sesión iniciada).
public class CategoriasActivity extends ProtegidaActivity {

    private ArrayAdapter<String> adapter;
    private final List<String> textos = new ArrayList<>();
    private final List<Categoria> categorias = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_categorias);

        ListView lista = findViewById(R.id.lstCategorias);
        MaterialButton btnNueva = findViewById(R.id.btnNuevaCategoria);
        MaterialButton btnVolver = findViewById(R.id.btnVolver);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, textos);
        lista.setAdapter(adapter);

        btnNueva.setOnClickListener(v -> dialogoCategoria(null));
        lista.setOnItemClickListener((parent, view, pos, id) -> dialogoCategoria(categorias.get(pos)));
        btnVolver.setOnClickListener(v -> finish());

        cargar();
    }

    // Lee todas las categorías de SQLite y refresca la lista
    private void cargar() {
        textos.clear();
        categorias.clear();
        DBHelper helper = new DBHelper(this);
        try {
            for (Categoria c : helper.listarCategorias(false)) {
                categorias.add(c);
                String desc = (c.descripcion == null || c.descripcion.isEmpty())
                        ? "(sin descripción)" : c.descripcion;
                textos.add(c.nombreCategoria + "  •  " + (c.activo ? "Activa" : "INACTIVA") + "\n" + desc);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error al leer: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            helper.close();
        }
        adapter.notifyDataSetChanged();
    }

    // existente == null -> registrar; si no -> editar
    private void dialogoCategoria(Categoria existente) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_categoria, null);
        TextInputEditText txtNombre = v.findViewById(R.id.dlgNombreCategoria);
        TextInputEditText txtDesc = v.findViewById(R.id.dlgDescripcionCategoria);
        CheckBox chkActiva = v.findViewById(R.id.dlgActivaCategoria);

        if (existente != null) {
            txtNombre.setText(existente.nombreCategoria);
            txtDesc.setText(existente.descripcion);
            chkActiva.setChecked(existente.activo);
            chkActiva.setVisibility(View.VISIBLE);   // solo al editar
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(existente == null ? "Nueva categoría" : "Editar categoría")
                .setView(v)
                .setPositiveButton(existente == null ? "Registrar" : "Guardar", null)
                .setNegativeButton("Cancelar", null)
                .create();

        // Se sobreescribe el click para NO cerrar el diálogo si hay errores de validación
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String nombre = txtNombre.getText().toString().trim();
            String desc = txtDesc.getText().toString().trim();

            if (nombre.isEmpty()) {
                txtNombre.setError("El nombre es obligatorio");
                return;
            }

            DBHelper helper = new DBHelper(this);
            try {
                if (existente == null) {
                    helper.insertarCategoria(nombre, desc);
                    Toast.makeText(this, "Categoría registrada correctamente.", Toast.LENGTH_SHORT).show();
                } else {
                    helper.actualizarCategoria(existente.idCategoria, nombre, desc, chkActiva.isChecked());
                    Toast.makeText(this, "Categoría actualizada.", Toast.LENGTH_SHORT).show();
                }
                dialog.dismiss();
                cargar();
            } catch (SQLiteConstraintException e) {
                Toast.makeText(this, "Ya existe una categoría registrada con ese nombre",
                        Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            } finally {
                helper.close();
            }
        }));
        dialog.show();
    }
}