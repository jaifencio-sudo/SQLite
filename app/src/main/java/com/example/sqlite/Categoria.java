package com.example.sqlite;

// Modelo de la tabla CATEGORIA
public class Categoria {
    public long idCategoria;
    public String nombreCategoria;
    public String descripcion;
    public boolean activo;

    @Override
    public String toString() {
        return nombreCategoria; // Así se muestra en listas y desplegables
    }
}
