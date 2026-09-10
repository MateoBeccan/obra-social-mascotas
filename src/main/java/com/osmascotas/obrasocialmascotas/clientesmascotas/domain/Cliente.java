package com.osmascotas.obrasocialmascotas.clientesmascotas.domain;

import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.RolUsuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "cliente")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cliente_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(
            name = "usuario_id",
            unique = true,
            foreignKey = @ForeignKey(name = "fk_cliente_usuario")
    )
    private Usuario usuario;

    @Column(
            name = "dni",
            nullable = false,
            length = 50
    )
    private String dni;

    @Column(
            name = "nombre",
            nullable = false,
            length = 120
    )
    private String nombre;

    @Column(
            name = "apellido",
            nullable = false,
            length = 120
    )
    private String apellido;

    @Column(
            name = "correo_electronico",
            length = 254
    )
    private String correoElectronico;

    @Column(
            name = "telefono",
            length = 50
    )
    private String telefono;

    @Column(
            name = "domicilio",
            length = 500
    )
    private String domicilio;

    protected Cliente() {
        // Requerido por JPA.
    }

    public Cliente(
            String dni,
            String nombre,
            String apellido,
            String correoElectronico,
            String telefono,
            String domicilio
    ) {
        this(null, dni, nombre, apellido, correoElectronico, telefono, domicilio);
    }

    public Cliente(
            Usuario usuario,
            String dni,
            String nombre,
            String apellido,
            String correoElectronico,
            String telefono,
            String domicilio
    ) {
        this.usuario = usuario;
        this.dni = validarObligatorio(dni, "El DNI es obligatorio.");
        this.nombre = validarObligatorio(nombre, "El nombre es obligatorio.");
        this.apellido = validarObligatorio(apellido, "El apellido es obligatorio.");
        this.correoElectronico = validarOpcional(correoElectronico, "El correo electronico no puede estar vacio.");
        this.telefono = validarOpcional(telefono, "El telefono no puede estar vacio.");
        this.domicilio = validarOpcional(domicilio, "El domicilio no puede estar vacio.");
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getDni() {
        return dni;
    }

    public String getNombre() {
        return nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public String getCorreoElectronico() {
        return correoElectronico;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getDomicilio() {
        return domicilio;
    }

    public void asociarUsuario(Usuario usuario) {
        Objects.requireNonNull(usuario, "El usuario es obligatorio.");
        if (this.usuario != null) {
            throw new IllegalStateException("El cliente ya posee una cuenta asociada.");
        }
        if (usuario.getRolUsuario() != RolUsuario.CLIENTE) {
            throw new IllegalArgumentException("El usuario asociado debe tener rol CLIENTE.");
        }
        this.usuario = usuario;
    }

    private static String validarObligatorio(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor.trim();
    }

    private static String validarOpcional(String valor, String mensaje) {
        if (valor != null && valor.isBlank()) {
            throw new IllegalArgumentException(mensaje);
        }
        return valor == null ? null : valor.trim();
    }
}
