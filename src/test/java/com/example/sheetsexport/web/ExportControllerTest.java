package com.example.sheetsexport.web;

import com.example.sheetsexport.export.ResolvedExport;
import com.example.sheetsexport.service.GoogleSheetsExportService;
import com.example.sheetsexport.web.dto.SheetExportResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Client;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    private static final String INLINE_PAYLOAD = """
            {
              "sheetTitle": "Export test",
              "shareWithEmail": "user@example.com",
              "source": {
                "type": "inline",
                "rows": [ { "Produit": "Café", "Quantité": "12", "Statut": "" } ],
                "dropdownOptions": ["À commander", "En stock"],
                "dropdownColumnIndex": 2
              }
            }
            """;

    private static final String DATASET_PAYLOAD = """
            {
              "sheetTitle": "Export inventaire",
              "source": { "type": "dataset", "datasetId": "inventory", "filters": { "warehouse": "LYON-02" } }
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GoogleSheetsExportService exportService;

    @Test
    void createsSheetFromInlineSource() throws Exception {
        when(exportService.export(eq("access-token"), any()))
                .thenReturn(new SheetExportResponse("abc123",
                        "https://docs.google.com/spreadsheets/d/abc123/edit"));

        mockMvc.perform(post("/api/exports/sheet")
                        .with(oauth2Login())
                        .with(oauth2Client("google"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INLINE_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spreadsheetId").value("abc123"))
                .andExpect(jsonPath("$.spreadsheetUrl").value(
                        "https://docs.google.com/spreadsheets/d/abc123/edit"));
    }

    @Test
    void createsSheetFromDatasetSourceResolvedServerSide() throws Exception {
        when(exportService.export(eq("access-token"), any()))
                .thenReturn(new SheetExportResponse("def456",
                        "https://docs.google.com/spreadsheets/d/def456/edit"));

        mockMvc.perform(post("/api/exports/sheet")
                        .with(oauth2Login())
                        .with(oauth2Client("google"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DATASET_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spreadsheetId").value("def456"));

        // Les lignes viennent du serveur (filtre warehouse=LYON-02 → 3 lignes) et le dropdown
        // est celui du catalogue (colonne 3), pas quelque chose fourni par le client.
        ArgumentCaptor<ResolvedExport> captor = ArgumentCaptor.forClass(ResolvedExport.class);
        org.mockito.Mockito.verify(exportService).export(eq("access-token"), captor.capture());
        ResolvedExport resolved = captor.getValue();
        assertThat(resolved.rows()).hasSize(3);
        assertThat(resolved.rows().get(0)).containsKeys("Produit", "Quantité", "Catégorie", "Sous-Catégorie", "Entrepôt");
        assertThat(resolved.dropdownColumnIndex()).isEqualTo(2);
        assertThat(resolved.dropdownOptions()).containsExactly("Boissons", "Épicerie", "Fournitures");
        assertThat(resolved.hasDependentDropdown()).isTrue();
        assertThat(resolved.dependentColumnIndex()).isEqualTo(3);
        assertThat(resolved.dependentOptionsMap()).containsKey("Boissons");
    }

    @Test
    void returns400ForUnknownDataset() throws Exception {
        mockMvc.perform(post("/api/exports/sheet")
                        .with(oauth2Login())
                        .with(oauth2Client("google"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "sheetTitle": "x", "source": { "type": "dataset", "datasetId": "ghost" } }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/exports/sheet")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INLINE_PAYLOAD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidBodyWith400() throws Exception {
        mockMvc.perform(post("/api/exports/sheet")
                        .with(oauth2Login())
                        .with(oauth2Client("google"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "source": { "type": "inline", "rows": [], "dropdownOptions": [],
                                  "dropdownColumnIndex": -1 } }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.sheetTitle").exists());
    }

    @Test
    void exposesDatasetCatalogPublicly() throws Exception {
        mockMvc.perform(get("/api/datasets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("inventory"))
                .andExpect(jsonPath("$[0].dropdown.columnIndex").value(2))
                .andExpect(jsonPath("$[0].dropdown.dependentDropdown.dependentColumnIndex").value(3));
    }

    @Test
    void exposesDatasetRowsResolvedServerSide() throws Exception {
        mockMvc.perform(get("/api/datasets/inventory/rows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns").value(org.hamcrest.Matchers.contains(
                        "Produit", "Quantité", "Catégorie", "Sous-Catégorie", "Entrepôt")))
                .andExpect(jsonPath("$.rows.length()").value(8))
                .andExpect(jsonPath("$.rows[0]['Produit']").value("Café en grains 1kg"));

        mockMvc.perform(get("/api/datasets/inventory/rows").param("warehouse", "LILLE-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(1));
    }
}

