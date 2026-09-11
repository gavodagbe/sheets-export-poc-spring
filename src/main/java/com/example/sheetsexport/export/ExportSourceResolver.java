package com.example.sheetsexport.export;

import com.example.sheetsexport.dataset.DatasetCatalog;
import com.example.sheetsexport.dataset.DatasetDefinition;
import com.example.sheetsexport.dataset.DatasetProvider;
import com.example.sheetsexport.dataset.DropdownConfig;
import com.example.sheetsexport.web.dto.DatasetSource;
import com.example.sheetsexport.web.dto.ExportSource;
import com.example.sheetsexport.web.dto.InlineSource;
import com.example.sheetsexport.web.dto.SheetExportRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Transforme un {@link SheetExportRequest} (source polymorphe) en {@link ResolvedExport} à plat.
 *
 * <ul>
 *   <li>{@link InlineSource} → on prend les lignes / le dropdown fournis par le front tels quels ;</li>
 *   <li>{@link DatasetSource} → on va chercher les lignes via le {@link DatasetProvider} du
 *       {@link DatasetCatalog}, et la config du dropdown vient de la {@link DatasetDefinition}
 *       (le front n'a aucune prise dessus).</li>
 * </ul>
 */
@Component
public class ExportSourceResolver {

    private static final Logger log = LoggerFactory.getLogger(ExportSourceResolver.class);

    private final DatasetCatalog catalog;

    public ExportSourceResolver(DatasetCatalog catalog) {
        this.catalog = catalog;
    }

    public ResolvedExport resolve(SheetExportRequest request) {
        ExportSource source = request.source();
        if (source instanceof InlineSource inline) {
            return fromInline(request, inline);
        }
        if (source instanceof DatasetSource dataset) {
            return fromDataset(request, dataset);
        }
        // sealed interface : ne devrait jamais arriver
        throw new IllegalStateException("Type de source non géré : " + source.getClass());
    }

    private ResolvedExport fromInline(SheetExportRequest request, InlineSource inline) {
        log.info("Source inline : {} ligne(s) fournie(s) par le front", inline.rows().size());
        return new ResolvedExport(
                request.sheetTitle(),
                request.shareWithEmail(),
                inline.rows(),
                inline.dropdownOptions(),
                inline.dropdownColumnIndex());
    }

    private ResolvedExport fromDataset(SheetExportRequest request, DatasetSource source) {
        DatasetProvider provider = catalog.require(source.datasetId());   // 400 si inconnu
        DatasetDefinition def = provider.definition();

        List<Map<String, Object>> rawRows = provider.fetchRows(source.filtersOrEmpty());
        log.info("Source dataset '{}' (filtres={}) : {} ligne(s)",
                def.id(), source.filtersOrEmpty(), rawRows.size());

        // Entête du Sheet = les libellés du catalogue, dans l'ordre déclaré.
        List<Map<String, Object>> orderedRows = def.toLabeledRows(rawRows);

        DropdownConfig dropdown = def.dropdown();
        return new ResolvedExport(
                request.sheetTitle(),
                request.shareWithEmail(),
                orderedRows,
                dropdown.options(),
                dropdown.columnIndex());
    }
}
