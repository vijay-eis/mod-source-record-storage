package org.folio.services.handlers.match;

import static org.folio.DataImportEventTypes.DI_SRS_MARC_BIB_RECORD_MATCHED_READY_FOR_POST_PROCESSING;
import static org.folio.rest.jaxrs.model.DataImportEventTypes.DI_SRS_MARC_BIB_RECORD_MATCHED;
import static org.folio.rest.jaxrs.model.DataImportEventTypes.DI_SRS_MARC_BIB_RECORD_NOT_MATCHED;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.folio.dao.RecordDao;
import org.folio.services.util.TypeConnection;

/**
 * Handler for MARC-MARC matching/not-matching MARC bibliographic record by specific fields.
 */
@Component
public class MarcBibliographicMatchEventHandler extends AbstractMarcMatchEventHandler {

  @Autowired
  public MarcBibliographicMatchEventHandler(RecordDao recordDao) {
    super(TypeConnection.MARC_BIB, recordDao, DI_SRS_MARC_BIB_RECORD_MATCHED, DI_SRS_MARC_BIB_RECORD_NOT_MATCHED);
  }

  @Override
  public boolean isPostProcessingNeeded() {
    return true;
  }

  @Override
  public String getPostProcessingInitializationEventType() {
    return DI_SRS_MARC_BIB_RECORD_MATCHED_READY_FOR_POST_PROCESSING.value();
  }

}
