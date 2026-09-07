package com.osmascotas.obrasocialmascotas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		"app.security.jwt.issuer=obra-social-mascotas-test",
		"app.security.jwt.expiration=PT30M",
		"app.security.jwt.secret=01234567890123456789012345678901"
})
class ObraSocialMascotasApplicationTests {

	@Test
	void contextLoads() {
	}

}
