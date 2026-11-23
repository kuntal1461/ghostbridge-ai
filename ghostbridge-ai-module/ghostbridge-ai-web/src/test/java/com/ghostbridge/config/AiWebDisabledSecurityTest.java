package com.ghostbridge.config;

import com.ghostbridge.restcontroller.AiRestController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static com.ghostbridge.support.AiWebTestRequests.USAGE;
import static com.ghostbridge.support.AiWebTestRequests.aiUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The endpoints never exist when the shared security chain is switched off. */
@SpringBootTest(properties = "ghostbridge.security.enabled=false")
@AutoConfigureMockMvc
class AiWebDisabledSecurityTest {
    @Autowired
    private ApplicationContext context;
    @Autowired
    private MockMvc mvc;

    @Test
    void aiEndpointsAreNotRegisteredWithoutSharedSecurity() throws Exception {
        assertThat(context.getBeanNamesForType(AiRestController.class)).isEmpty();
        mvc.perform(get(USAGE).with(aiUser("someone"))).andExpect(status().isNotFound());
    }
}
