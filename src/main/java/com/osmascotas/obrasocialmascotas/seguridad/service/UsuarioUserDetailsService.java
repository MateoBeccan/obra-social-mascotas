package com.osmascotas.obrasocialmascotas.seguridad.service;

import com.osmascotas.obrasocialmascotas.seguridad.domain.EstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioUserDetailsService implements UserDetailsService {

    private static final String PREFIJO_ROL = "ROLE_";

    private final UsuarioRepository usuarioRepository;

    public UsuarioUserDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.buscarPorIdentificadorAcceso(username)
                .orElseThrow(() -> new UsernameNotFoundException("No existe un usuario con identificador " + username + "."));

        EstadoUsuario estadoUsuario = usuario.getEstadoUsuario();

        return User.builder()
                .username(usuario.getIdentificadorAcceso())
                .password(usuario.getContrasenaHash())
                .authorities(new SimpleGrantedAuthority(PREFIJO_ROL + usuario.getRolUsuario().name()))
                .disabled(estadoUsuario == EstadoUsuario.INACTIVO)
                .accountLocked(estadoUsuario == EstadoUsuario.BLOQUEADO)
                .build();
    }
}
