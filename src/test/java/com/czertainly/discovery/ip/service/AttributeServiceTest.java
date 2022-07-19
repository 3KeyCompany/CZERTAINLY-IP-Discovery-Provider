package com.czertainly.discovery.ip.service;

import com.czertainly.api.model.common.attribute.AttributeDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class AttributeServiceTest {
    @Autowired
    private AttributeService attributeService;

    @Test
    public void testAttributeResponse() {
        List<AttributeDefinition> attributes = attributeService.getAttributes("IP-Hostname");
        Assertions.assertNotNull(attributes);
    }
}