package com.ldsilver.chingoohaja;

import com.ldsilver.chingoohaja.config.FirebaseConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class ChingooHajaApplicationTests {

	// Firebase는 실제 서비스 계정 파일이 필요하므로 테스트 환경에서 mock으로 대체
	@MockitoBean
	FirebaseConfig firebaseConfig;

	@Test
	void contextLoads() {
	}

}
