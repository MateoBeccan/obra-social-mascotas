package com.osmascotas.obrasocialmascotas;

import com.osmascotas.obrasocialmascotas.auditoria.repository.RegistroAuditoriaRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Cliente;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Mascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.TitularidadMascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.ClienteRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.MascotaRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.repository.TitularidadMascotaRepository;
import com.osmascotas.obrasocialmascotas.clientesmascotas.service.ActualizarFotografiaMascotaPropiaService;
import com.osmascotas.obrasocialmascotas.seguridad.domain.EstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.RolUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import com.osmascotas.obrasocialmascotas.seguridad.repository.ActivacionCuentaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.RecuperacionContrasenaTokenRepository;
import com.osmascotas.obrasocialmascotas.seguridad.repository.UsuarioRepository;
import com.osmascotas.obrasocialmascotas.seguridad.service.JwtService;
import com.osmascotas.obrasocialmascotas.storage.AlmacenamientoNoDisponibleException;
import com.osmascotas.obrasocialmascotas.storage.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({
        TestcontainersConfiguration.class,
        ActualizarFotografiaMascotaPropiaIntegrationTest.ObjectStorageTestConfiguration.class
})
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "app.security.jwt.issuer=obra-social-mascotas-test",
        "app.security.jwt.expiration=PT30M",
        "app.security.jwt.secret=01234567890123456789012345678901",
        "app.security.password-recovery.expiration=PT15M",
        "app.security.account-activation.expiration=PT24H"
})
class ActualizarFotografiaMascotaPropiaIntegrationTest {

    private static final String CONTRASENA = "Password123!";
    private static final long MAX_FOTOGRAFIA_BYTES = 5L * 1024L * 1024L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private MascotaRepository mascotaRepository;

    @Autowired
    private TitularidadMascotaRepository titularidadMascotaRepository;

    @Autowired
    private RegistroAuditoriaRepository registroAuditoriaRepository;

    @Autowired
    private RecuperacionContrasenaTokenRepository recuperacionContrasenaTokenRepository;

    @Autowired
    private ActivacionCuentaTokenRepository activacionCuentaTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ActualizarFotografiaMascotaPropiaService actualizarFotografiaMascotaPropiaService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private InMemoryObjectStorageService objectStorageService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        objectStorageService.clear();
        titularidadMascotaRepository.deleteAll();
        mascotaRepository.deleteAll();
        clienteRepository.deleteAll();
        registroAuditoriaRepository.deleteAll();
        activacionCuentaTokenRepository.deleteAll();
        recuperacionContrasenaTokenRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void actualizarFotografiaConJpegValidoDeMascotaPropiaDevuelveNoContentYGuardaKeyPrivada() throws Exception {
        Usuario usuario = guardarUsuario("cliente-foto-jpg@test.local", RolUsuario.CLIENTE);
        Cliente cliente = guardarCliente(usuario, "11111111", "Ana");
        Mascota mascota = guardarMascota("Luna", null);
        guardarTitularidad(mascota, cliente, LocalDate.of(2026, 9, 1), null);

        mockMvc.perform(multipartPut(mascota.getId(), archivoJpegValido())
                        .header("Authorization", "Bearer " + tokenValido(usuario)))
                .andExpect(status().isNoContent())
                .andExpect(cookie().doesNotExist("JSESSIONID"));

        Mascota mascotaActualizada = mascotaRepository.findById(mascota.getId()).orElseThrow();
        assertThat(mascotaActualizada.getFotografiaObjetoKey())
                .startsWith("mascotas/" + mascota.getId() + "/fotografias/")
                .endsWith(".jpg")
                .doesNotContain("foto-original");
        assertThat(objectStorageService.objetos()).containsOnlyKeys(mascotaActualizada.getFotografiaObjetoKey());
        assertThat(objectStorageService.objetos().get(mascotaActualizada.getFotografiaObjetoKey()).contentType())
                .isEqualTo(MediaType.IMAGE_JPEG_VALUE);
        assertThat(objectStorageService.eliminados()).isEmpty();
        assertConteos(1, 1, 1, 1, 0);
    }

    @Test
    void actualizarFotografiaConPngValidoDeMascotaPropiaUsaExtensionPng() throws Exception {
        Usuario usuario = guardarUsuario("cliente-foto-png@test.local", RolUsuario.CLIENTE);
        Cliente cliente = guardarCliente(usuario, "22222222", "Bruno");
        Mascota mascota = guardarMascota("Milo", null);
        guardarTitularidad(mascota, cliente, LocalDate.of(2026, 9, 1), null);

        mockMvc.perform(multipartPut(mascota.getId(), archivoPngValido())
                        .header("Authorization", "Bearer " + tokenValido(usuario)))
                .andExpect(status().isNoContent());

        Mascota mascotaActualizada = mascotaRepository.findById(mascota.getId()).orElseThrow();
        assertThat(mascotaActualizada.getFotografiaObjetoKey()).endsWith(".png");
        assertThat(objectStorageService.objetos().get(mascotaActualizada.getFotografiaObjetoKey()).contentType())
                .isEqualTo(MediaType.IMAGE_PNG_VALUE);
        assertConteos(1, 1, 1, 1, 0);
    }

    @Test
    void actualizarFotografiaReemplazaKeyYEliminaObjetoAnteriorLuegoDelCommit() throws Exception {
        Usuario usuario = guardarUsuario("cliente-reemplazo@test.local", RolUsuario.CLIENTE);
        Cliente cliente = guardarCliente(usuario, "33333333", "Clara");
        String keyAnterior = "mascotas/anterior/fotografias/vieja.jpg";
        Mascota mascota = guardarMascota("Nina", keyAnterior);
        guardarTitularidad(mascota, cliente, LocalDate.of(2026, 9, 1), null);
        objectStorageService.objetos().put(keyAnterior, new StoredObject(new byte[]{1}, MediaType.IMAGE_JPEG_VALUE));

        mockMvc.perform(multipartPut(mascota.getId(), archivoJpegValido())
                        .header("Authorization", "Bearer " + tokenValido(usuario)))
                .andExpect(status().isNoContent());

        Mascota mascotaActualizada = mascotaRepository.findById(mascota.getId()).orElseThrow();
        assertThat(mascotaActualizada.getFotografiaObjetoKey()).isNotEqualTo(keyAnterior);
        assertThat(objectStorageService.eliminados()).containsExactly(keyAnterior);
        assertThat(objectStorageService.objetos()).doesNotContainKey(keyAnterior);
        assertThat(objectStorageService.objetos()).containsKey(mascotaActualizada.getFotografiaObjetoKey());
        assertConteos(1, 1, 1, 1, 0);
    }

    @Test
    void actualizarFotografiaDeMascotaDeOtroClienteDevuelveNotFoundSinUpload() throws Exception {
        Usuario usuarioA = guardarUsuario("cliente-a-foto@test.local", RolUsuario.CLIENTE);
        Usuario usuarioB = guardarUsuario("cliente-b-foto@test.local", RolUsuario.CLIENTE);
        guardarCliente(usuarioA, "44444444", "Diego");
        Cliente clienteB = guardarCliente(usuarioB, "55555555", "Elena");
        Mascota mascotaB = guardarMascota("Olivia", null);
        guardarTitularidad(mascotaB, clienteB, LocalDate.of(2026, 9, 1), null);

        mockMvc.perform(multipartPut(mascotaB.getId(), archivoJpegValido())
                        .header("Authorization", "Bearer " + tokenValido(usuarioA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No se encontro la Mascota entre las mascotas actuales del Cliente"));

        assertThat(mascotaRepository.findById(mascotaB.getId()).orElseThrow().getFotografiaObjetoKey()).isNull();
        assertStorageSinCambios();
    }

    @Test
    void actualizarFotografiaDeTitularidadHistoricaDevuelveNotFoundSinUpload() throws Exception {
        Usuario usuario = guardarUsuario("cliente-historico-foto@test.local", RolUsuario.CLIENTE);
        Cliente cliente = guardarCliente(usuario, "66666666", "Fabiana");
        Mascota mascota = guardarMascota("Toto", null);
        guardarTitularidad(mascota, cliente, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 1));

        mockMvc.perform(multipartPut(mascota.getId(), archivoJpegValido())
                        .header("Authorization", "Bearer " + tokenValido(usuario)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No se encontro la Mascota entre las mascotas actuales del Cliente"));

        assertThat(mascotaRepository.findById(mascota.getId()).orElseThrow().getFotografiaObjetoKey()).isNull();
        assertStorageSinCambios();
    }

    @Test
    void actualizarFotografiaDeMascotaInexistenteDevuelveNotFoundSinUpload() throws Exception {
        Usuario usuario = guardarUsuario("cliente-inexistente-foto@test.local", RolUsuario.CLIENTE);
        guardarCliente(usuario, "77777777", "Gabriel");

        mockMvc.perform(multipartPut(999999L, archivoJpegValido())
                        .header("Authorization", "Bearer " + tokenValido(usuario)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No se encontro la Mascota entre las mascotas actuales del Cliente"));

        assertStorageSinCambios();
    }

    @Test
    void actualizarFotografiaSinPerfilClienteDevuelveNotFoundSinUpload() throws Exception {
        Usuario usuario = guardarUsuario("cliente-sin-perfil-foto@test.local", RolUsuario.CLIENTE);

        mockMvc.perform(multipartPut(1L, archivoJpegValido())
                        .header("Authorization", "Bearer " + tokenValido(usuario)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No se encontro el perfil de Cliente asociado a la cuenta"));

        assertStorageSinCambios();
    }

    @Test
    void actualizarFotografiaSinJwtDevuelveUnauthorized() throws Exception {
        mockMvc.perform(multipartPut(1L, archivoJpegValido()))
                .andExpect(status().isUnauthorized());

        assertStorageSinCambios();
    }

    @Test
    void actualizarFotografiaConAdministradorDevuelveForbidden() throws Exception {
        Usuario administrador = guardarUsuario("admin-foto@test.local", RolUsuario.ADMINISTRADOR);

        mockMvc.perform(multipartPut(1L, archivoJpegValido())
                        .header("Authorization", "Bearer " + tokenValido(administrador)))
                .andExpect(status().isForbidden());

        assertStorageSinCambios();
    }

    @Test
    void actualizarFotografiaVaciaDevuelveBadRequestSinModificarKeyAnterior() throws Exception {
        Usuario usuario = guardarUsuario("cliente-vacia@test.local", RolUsuario.CLIENTE);
        Cliente cliente = guardarCliente(usuario, "88888888", "Helena");
        String keyAnterior = "mascotas/anterior/fotografias/vieja.jpg";
        Mascota mascota = guardarMascota("Mora", keyAnterior);
        guardarTitularidad(mascota, cliente, LocalDate.of(2026, 9, 1), null);

        mockMvc.perform(multipartPut(mascota.getId(), new MockMultipartFile(
                        "archivo",
                        "foto.jpg",
                        MediaType.IMAGE_JPEG_VALUE,
                        new byte[0]
                ))
                        .header("Authorization", "Bearer " + tokenValido(usuario)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La fotografia no puede estar vacia"));

        assertThat(mascotaRepository.findById(mascota.getId()).orElseThrow().getFotografiaObjetoKey())
                .isEqualTo(keyAnterior);
        assertStorageSinCambios();
    }

    @Test
    void actualizarFotografiaConContentTypeNoPermitidoDevuelveBadRequest() throws Exception {
        Usuario usuario = guardarUsuario("cliente-txt@test.local", RolUsuario.CLIENTE);
        Cliente cliente = guardarCliente(usuario, "99990000", "Ivana");
        Mascota mascota = guardarMascota("Simba", null);
        guardarTitularidad(mascota, cliente, LocalDate.of(2026, 9, 1), null);

        mockMvc.perform(multipartPut(mascota.getId(), new MockMultipartFile(
                        "archivo",
                        "foto.txt",
                        MediaType.TEXT_PLAIN_VALUE,
                        "texto".getBytes(StandardCharsets.UTF_8)
                ))
                        .header("Authorization", "Bearer " + tokenValido(usuario)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La fotografia debe ser JPEG o PNG"));

        assertThat(mascotaRepository.findById(mascota.getId()).orElseThrow().getFotografiaObjetoKey()).isNull();
        assertStorageSinCambios();
    }

    @Test
    void actualizarFotografiaSinContentTypeDevuelveBadRequestSinUpload() throws Exception {
        Usuario usuario = guardarUsuario("cliente-content-type-null@test.local", RolUsuario.CLIENTE);
        Cliente cliente = guardarCliente(usuario, "99990111", "Isabel");
        String keyAnterior = "mascotas/anterior/fotografias/vieja.jpg";
        Mascota mascota = guardarMascota("Sol", keyAnterior);
        guardarTitularidad(mascota, cliente, LocalDate.of(2026, 9, 1), null);

        mockMvc.perform(multipartPut(mascota.getId(), new MockMultipartFile(
                        "archivo",
                        "foto.jpg",
                        null,
                        imagenValida("jpg")
                ))
                        .header("Authorization", "Bearer " + tokenValido(usuario)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La fotografia debe ser JPEG o PNG"));

        assertThat(mascotaRepository.findById(mascota.getId()).orElseThrow().getFotografiaObjetoKey())
                .isEqualTo(keyAnterior);
        assertStorageSinCambios();
    }

    @Test
    void actualizarFotografiaJpegFalseadoDevuelveBadRequest() throws Exception {
        Usuario usuario = guardarUsuario("cliente-spoof-jpg@test.local", RolUsuario.CLIENTE);
        Cliente cliente = guardarCliente(usuario, "99991111", "Julieta");
        Mascota mascota = guardarMascota("Felix", null);
        guardarTitularidad(mascota, cliente, LocalDate.of(2026, 9, 1), null);

        mockMvc.perform(multipartPut(mascota.getId(), new MockMultipartFile(
                        "archivo",
                        "foto.jpg",
                        MediaType.IMAGE_JPEG_VALUE,
                        "no soy jpeg".getBytes(StandardCharsets.UTF_8)
                ))
                        .header("Authorization", "Bearer " + tokenValido(usuario)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El contenido de la fotografia no es una imagen valida"));

        assertThat(mascotaRepository.findById(mascota.getId()).orElseThrow().getFotografiaObjetoKey()).isNull();
        assertStorageSinCambios();
    }

    @Test
    void actualizarFotografiaPngFalseadoDevuelveBadRequest() throws Exception {
        Usuario usuario = guardarUsuario("cliente-spoof-png@test.local", RolUsuario.CLIENTE);
        Cliente cliente = guardarCliente(usuario, "99992222", "Karina");
        Mascota mascota = guardarMascota("Teo", null);
        guardarTitularidad(mascota, cliente, LocalDate.of(2026, 9, 1), null);

        mockMvc.perform(multipartPut(mascota.getId(), new MockMultipartFile(
                        "archivo",
                        "foto.png",
                        MediaType.IMAGE_PNG_VALUE,
                        "no soy png".getBytes(StandardCharsets.UTF_8)
                ))
                        .header("Authorization", "Bearer " + tokenValido(usuario)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El contenido de la fotografia no es una imagen valida"));

        assertThat(mascotaRepository.findById(mascota.getId()).orElseThrow().getFotografiaObjetoKey()).isNull();
        assertStorageSinCambios();
    }

    @Test
    void actualizarFotografiaMayorACincoMibDevuelveBadRequestSinUpload() throws Exception {
        Usuario usuario = guardarUsuario("cliente-grande@test.local", RolUsuario.CLIENTE);
        Cliente cliente = guardarCliente(usuario, "99993333", "Laura");
        Mascota mascota = guardarMascota("Roco", null);
        guardarTitularidad(mascota, cliente, LocalDate.of(2026, 9, 1), null);

        mockMvc.perform(multipartPut(mascota.getId(), new MockMultipartFile(
                        "archivo",
                        "foto.png",
                        MediaType.IMAGE_PNG_VALUE,
                        new byte[(int) MAX_FOTOGRAFIA_BYTES + 1]
                ))
                        .header("Authorization", "Bearer " + tokenValido(usuario)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La fotografia supera el tamano maximo permitido"));

        assertThat(mascotaRepository.findById(mascota.getId()).orElseThrow().getFotografiaObjetoKey()).isNull();
        assertStorageSinCambios();
    }

    @Test
    void actualizarFotografiaCuandoFallaUploadDevuelveServiceUnavailableSinModificarDbNiEliminarAnterior() throws Exception {
        Usuario usuario = guardarUsuario("cliente-storage-falla@test.local", RolUsuario.CLIENTE);
        Cliente cliente = guardarCliente(usuario, "99994444", "Marcela");
        String keyAnterior = "mascotas/anterior/fotografias/vieja.jpg";
        Mascota mascota = guardarMascota("Chispa", keyAnterior);
        guardarTitularidad(mascota, cliente, LocalDate.of(2026, 9, 1), null);
        objectStorageService.fallarGuardado();

        mockMvc.perform(multipartPut(mascota.getId(), archivoJpegValido())
                        .header("Authorization", "Bearer " + tokenValido(usuario)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value("Almacenamiento no disponible"));

        assertThat(mascotaRepository.findById(mascota.getId()).orElseThrow().getFotografiaObjetoKey())
                .isEqualTo(keyAnterior);
        assertThat(objectStorageService.objetos()).isEmpty();
        assertThat(objectStorageService.eliminados()).isEmpty();
    }

    @Test
    void actualizarFotografiaConRollbackPostUploadEliminaKeyNuevaYConservaAnterior() throws Exception {
        Usuario usuario = guardarUsuario("cliente-rollback-post-upload@test.local", RolUsuario.CLIENTE);
        Cliente cliente = guardarCliente(usuario, "99994777", "Milagros");
        String keyAnterior = "mascotas/anterior/fotografias/vieja.jpg";
        Mascota mascota = guardarMascota("Pipa", keyAnterior);
        guardarTitularidad(mascota, cliente, LocalDate.of(2026, 9, 1), null);
        objectStorageService.objetos().put(keyAnterior, new StoredObject(new byte[]{1}, MediaType.IMAGE_JPEG_VALUE));

        autenticar(usuario);
        transactionTemplate.executeWithoutResult(status -> {
            try {
                actualizarFotografiaMascotaPropiaService.actualizar(mascota.getId(), archivoJpegValido());
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            assertThat(objectStorageService.guardados()).hasSize(1);
            assertThat(objectStorageService.objetos()).containsKey(objectStorageService.guardados().getFirst());
            status.setRollbackOnly();
        });
        SecurityContextHolder.clearContext();

        String keyNueva = objectStorageService.guardados().getFirst();
        assertThat(keyNueva).startsWith("mascotas/" + mascota.getId() + "/fotografias/");
        assertThat(keyNueva).isNotEqualTo(keyAnterior);
        assertThat(mascotaRepository.findById(mascota.getId()).orElseThrow().getFotografiaObjetoKey())
                .isEqualTo(keyAnterior);
        assertThat(objectStorageService.objetos()).containsOnlyKeys(keyAnterior);
        assertThat(objectStorageService.eliminados()).containsExactly(keyNueva);
        assertThat(objectStorageService.eliminados()).doesNotContain(keyAnterior);
        assertThat(registroAuditoriaRepository.count()).isZero();
    }

    @Test
    void actualizarFotografiaNoCreaAuditoria() throws Exception {
        Usuario usuario = guardarUsuario("cliente-no-auditoria@test.local", RolUsuario.CLIENTE);
        Cliente cliente = guardarCliente(usuario, "99995555", "Natalia");
        Mascota mascota = guardarMascota("Beto", null);
        guardarTitularidad(mascota, cliente, LocalDate.of(2026, 9, 1), null);

        mockMvc.perform(multipartPut(mascota.getId(), archivoJpegValido())
                        .header("Authorization", "Bearer " + tokenValido(usuario)))
                .andExpect(status().isNoContent());

        assertThat(registroAuditoriaRepository.count()).isZero();
        assertThat(activacionCuentaTokenRepository.count()).isZero();
        assertThat(recuperacionContrasenaTokenRepository.count()).isZero();
    }

    @Test
    void consultarMascotasDespuesDeActualizarFotografiaNoExponeObjectKey() throws Exception {
        Usuario usuario = guardarUsuario("cliente-no-expone-key@test.local", RolUsuario.CLIENTE);
        Cliente cliente = guardarCliente(usuario, "99996666", "Olga");
        Mascota mascota = guardarMascota("Tina", null);
        guardarTitularidad(mascota, cliente, LocalDate.of(2026, 9, 1), null);

        mockMvc.perform(multipartPut(mascota.getId(), archivoJpegValido())
                        .header("Authorization", "Bearer " + tokenValido(usuario)))
                .andExpect(status().isNoContent());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/clientes/me/mascotas")
                        .header("Authorization", "Bearer " + tokenValido(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(mascota.getId()))
                .andExpect(jsonPath("$[0].fotografiaObjetoKey").doesNotExist());
    }

    private Usuario guardarUsuario(String identificadorAcceso, RolUsuario rolUsuario) {
        Usuario usuario = new Usuario(
                identificadorAcceso,
                passwordEncoder.encode(CONTRASENA),
                rolUsuario,
                EstadoUsuario.ACTIVO,
                identificadorAcceso
        );

        return usuarioRepository.saveAndFlush(usuario);
    }

    private Cliente guardarCliente(Usuario usuario, String dni, String nombre) {
        return clienteRepository.saveAndFlush(new Cliente(
                usuario,
                dni,
                nombre,
                "Apellido",
                nombre.toLowerCase() + "@test.local",
                "3410000000",
                "Calle Test 123"
        ));
    }

    private Mascota guardarMascota(String nombre, String fotografiaObjetoKey) {
        return mascotaRepository.saveAndFlush(new Mascota(
                nombre,
                "PERRO",
                "Mestiza",
                "HEMBRA",
                LocalDate.of(2022, 4, 15),
                fotografiaObjetoKey
        ));
    }

    private TitularidadMascota guardarTitularidad(
            Mascota mascota,
            Cliente cliente,
            LocalDate fechaDesde,
            LocalDate fechaHasta
    ) {
        return titularidadMascotaRepository.saveAndFlush(new TitularidadMascota(
                mascota,
                cliente,
                fechaDesde,
                fechaHasta,
                null,
                null
        ));
    }

    private String tokenValido(Usuario usuario) {
        return jwtService.generarAccessToken(UsernamePasswordAuthenticationToken.authenticated(
                usuario.getIdentificadorAcceso(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRolUsuario().name()))
        ));
    }

    private void autenticar(Usuario usuario) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                usuario.getIdentificadorAcceso(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRolUsuario().name()))
        ));
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder multipartPut(
            Long mascotaId,
            MultipartFile archivo
    ) throws IOException {
        return (org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder) multipart(
                "/api/clientes/me/mascotas/{mascotaId}/fotografia",
                mascotaId
        )
                .file(new MockMultipartFile(
                        archivo.getName(),
                        archivo.getOriginalFilename(),
                        archivo.getContentType(),
                        archivo.getBytes()
                ))
                .with(request -> {
                    request.setMethod("PUT");
                    return request;
                });
    }

    private MockMultipartFile archivoJpegValido() throws IOException {
        return new MockMultipartFile(
                "archivo",
                "foto-original.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                imagenValida("jpg")
        );
    }

    private MockMultipartFile archivoPngValido() throws IOException {
        return new MockMultipartFile(
                "archivo",
                "foto-original.png",
                MediaType.IMAGE_PNG_VALUE,
                imagenValida("png")
        );
    }

    private byte[] imagenValida(String formato) throws IOException {
        BufferedImage image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        image.setRGB(1, 1, Color.GREEN.getRGB());
        image.setRGB(2, 2, Color.BLUE.getRGB());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, formato, outputStream);
        return outputStream.toByteArray();
    }

    private void assertStorageSinCambios() {
        assertThat(objectStorageService.objetos()).isEmpty();
        assertThat(objectStorageService.eliminados()).isEmpty();
    }

    private void assertConteos(
            long usuariosEsperados,
            long clientesEsperados,
            long mascotasEsperadas,
            long titularidadesEsperadas,
            long auditoriasEsperadas
    ) {
        assertThat(usuarioRepository.count()).isEqualTo(usuariosEsperados);
        assertThat(clienteRepository.count()).isEqualTo(clientesEsperados);
        assertThat(mascotaRepository.count()).isEqualTo(mascotasEsperadas);
        assertThat(titularidadMascotaRepository.count()).isEqualTo(titularidadesEsperadas);
        assertThat(registroAuditoriaRepository.count()).isEqualTo(auditoriasEsperadas);
        assertThat(activacionCuentaTokenRepository.count()).isZero();
        assertThat(recuperacionContrasenaTokenRepository.count()).isZero();
    }

    @TestConfiguration
    static class ObjectStorageTestConfiguration {

        @Bean
        @Primary
        InMemoryObjectStorageService objectStorageService() {
            return new InMemoryObjectStorageService();
        }
    }

    static class InMemoryObjectStorageService implements ObjectStorageService {

        private final Map<String, StoredObject> objetos = new LinkedHashMap<>();
        private final List<String> guardados = new ArrayList<>();
        private final List<String> eliminados = new ArrayList<>();
        private boolean fallarGuardado;

        @Override
        public void guardar(String objectKey, byte[] contenido, String contentType) {
            if (fallarGuardado) {
                throw new AlmacenamientoNoDisponibleException(
                        "Fallo de almacenamiento en test.",
                        new RuntimeException("Fallo controlado")
                );
            }
            guardados.add(objectKey);
            objetos.put(objectKey, new StoredObject(contenido, contentType));
        }

        @Override
        public void eliminar(String objectKey) {
            eliminados.add(objectKey);
            objetos.remove(objectKey);
        }

        void fallarGuardado() {
            fallarGuardado = true;
        }

        void clear() {
            objetos.clear();
            guardados.clear();
            eliminados.clear();
            fallarGuardado = false;
        }

        Map<String, StoredObject> objetos() {
            return objetos;
        }

        List<String> guardados() {
            return guardados;
        }

        List<String> eliminados() {
            return eliminados;
        }
    }

    record StoredObject(byte[] contenido, String contentType) {
    }
}
