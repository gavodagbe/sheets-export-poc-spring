package com.example.sheetsexport.web;

import com.example.sheetsexport.service.GoogleSheetsExportService;
import com.example.sheetsexport.web.dto.SheetExportResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Client;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'intégration web : le service Google est mocké, la session OAuth2 est simulée.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret"
})
@AutoConfigureMockMvc
class ExportControllerTest {

    private static final String VALID_PAYLOAD = """
            {
              "sheetTitle": "Export test",
              "shareWithEmail": "user@example.com",
              "rows": [ { "Produit": "Café", "Quantité": "12", "Statut": "" } ],
              "dropdownOptions": ["À commander", "En stock"],
              "dropdownColumnIndex": 2
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GoogleSheetsExportService exportService;

    @Test
    void createsSheetForAuthenticatedUser() throws Exception {
        // Le post-processor oauth2Client("google") fournit un token dont la valeur est "access-token".
        when(exportService.export(eq("access-token"), any()))
                .thenReturn(new SheetExportResponse("abc123",
                        "https://docs.google.com/spreadsheets/d/abc123/edit"));

        mockMvc.perform(post("/api/exports/sheet")
                        .with(oauth2Login())
                        .with(oauth2Client("google"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spreadsheetId").value("abc123"))
                .andExpect(jsonPath("$.spreadsheetUrl").value(
                        "https://docs.google.com/spreadsheets/d/abc123/edit"));
    }

    @Test
    void returns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/exports/sheet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidBodyWith400() throws Exception {
        mockMvc.perform(post("/api/exports/sheet")
                        .with(oauth2Login())
                        .with(oauth2Client("google"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "rows": [], "dropdownOptions": [], "dropdownColumnIndex": -1 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.sheetTitle").exists());
    }
}
