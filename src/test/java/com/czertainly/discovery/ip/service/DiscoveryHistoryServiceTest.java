package com.czertainly.discovery.ip.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.connector.discovery.DiscoveryRequestDto;
import com.czertainly.discovery.ip.dao.DiscoveryHistory;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;

@SpringBootTest
@Transactional
@Rollback
public class DiscoveryHistoryServiceTest {
    @Autowired
    private DiscoveryHistoryService discoveryHistoryService;

    private DiscoveryRequestDto discoveryProviderDto;

    @BeforeEach
    public void setUp() {
        discoveryProviderDto = new DiscoveryRequestDto();
        discoveryProviderDto.setName("test");
    }

    @Test
    public void testAddDiscovery(){
        DiscoveryHistory history = discoveryHistoryService.addHistory(discoveryProviderDto);
        Assertions.assertNotNull(history);
    }

    @Test
    public void testGetDiscoveryById(){
        Assertions.assertThrows(NotFoundException.class, () -> discoveryHistoryService.getHistoryById(12312L));
    }
}
