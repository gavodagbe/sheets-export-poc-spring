package com.example.sheetsexport.web;

import com.example.sheetsexport.export.ExportSourceResolver;
import com.example.sheetsexport.export.ResolvedExport;
import com.example.sheetsexport.service.GoogleSheetsExportService;
import com.example.sheetsexport.web.dto.SheetExportRequest;
import com.example.sheetsexport.web.dto.SheetExportResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exports")
public class ExportController {

    private static final Logger log = LoggerFactory.getLogger(ExportController.class);

    private final ExportSourceResolver sourceResolver;
    private final GoogleSheetsExportService exportService;

    public ExportController(ExportSourceResolver sourceResolver, GoogleSheetsExportService exportService) {
        this.sourceResolver = sourceResolver;
        this.exportService = exportService;
    }

    /**
     * L'utilisateur doit avoir une session authentifiée Google (sinon 401 → le front
     * redirige vers le consentement). Le token est récupéré depuis Spring Security,
     * jamais exposé au navigateur.
     *
     * <p>Les données à exporter sont soit fournies par le front ({@code source.type = "inline"}),
     * soit résolues côté serveur depuis un jeu de données ({@code source.type = "dataset"}).</p>
     */
    @PostMapping("/sheet")
    public SheetExportResponse exportSheet(
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
            @Valid @RequestBody SheetExportRequest request) {

        String accessToken = authorizedClient.getAccessToken().getTokenValue();
        ResolvedExport resolved = sourceResolver.resolve(request);
        log.info("Demande d'export : title='{}', source={}, rows={}, share='{}'",
                resolved.sheetTitle(), request.source().getClass().getSimpleName(),
                resolved.rows().size(), resolved.shareWithEmail());
        return exportService.export(accessToken, resolved);
    }
}
