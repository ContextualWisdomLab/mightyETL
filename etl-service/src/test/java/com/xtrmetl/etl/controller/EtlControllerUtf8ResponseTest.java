package com.xtrmetl.etl.controller;

import com.xtrmetl.etl.connector.ConnectorProperties;
import com.xtrmetl.etl.connector.TargetConnectorDispatcher;
import com.xtrmetl.etl.connector.TargetConnectorRegistry;
import com.xtrmetl.etl.service.EtlService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EtlControllerUtf8ResponseTest {

    @Test
    void successfulUnicodeResultDeclaresUtf8TextRepresentation() throws Exception {
        EtlService etlService = mock(EtlService.class);
        TargetConnectorDispatcher dispatcher = new TargetConnectorDispatcher(
                new TargetConnectorRegistry(),
                new ConnectorProperties()
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new EtlController(etlService, dispatcher)
                )
                .setControllerAdvice(new EtlApiProblemHandler())
                .build();

        String request = "[{\"id\":\"레코드_α\"}]";
        String response = "Processed: 레코드_α";
        when(etlService.processData(request)).thenReturn(response);

        mockMvc.perform(post("/api/etl/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"))
                .andExpect(content().string(response));
    }
}
