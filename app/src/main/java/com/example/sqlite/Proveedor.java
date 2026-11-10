package com.example.sqlite;

// Modelo de la tabla PROVEEDOR
public class Proveedor {
    public long idProveedor;
    public String rutEmpresa;
    public String razonSocial;
    public String numeroContacto;
    public String direccion;

    public static String normalizarRut(String rut) {
        if (rut == null) return null;
        String limpio = rut.replace(".", "").replace(" ", "").toUpperCase();
        if (!limpio.matches("\\d{7,8}-?[0-9K]")) return null;
        if (!limpio.contains("-")) {
            limpio = limpio.substring(0, limpio.length() - 1) + "-" + limpio.substring(limpio.length() - 1);
        }
        return limpio;
    }

    // Número de contacto: al menos 8 dígitos; se permiten +, espacios y guiones
    public static boolean telefonoValido(String tel) {
        if (tel == null || !tel.matches("[0-9+\\-\\s()]+")) return false;
        return tel.replaceAll("\\D", "").length() >= 8;
    }
}
