package com.jeongjungang;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** 전체 설정으로 컨텍스트가 뜨는지 확인한다. Redis 연결은 처음 쓸 때 맺어지므로 Redis 없이도 통과해야 한다. */
@SpringBootTest
class JeongjungangApplicationTests {

    @Test
    void contextLoads() {
    }
}
