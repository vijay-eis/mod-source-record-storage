package org.folio.services.handlers.match;

import static org.folio.rest.jaxrs.model.DataImportEventTypes.DI_SRS_MARC_AUTHORITY_RECORD_MATCHED;
import static org.folio.rest.jaxrs.model.DataImportEventTypes.DI_SRS_MARC_AUTHORITY_RECORD_NOT_MATCHED;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.folio.dao.RecordDao;
import org.folio.services.util.TypeConnection;

/**
 * Handler for MARC-MARC matching/not-matching MARC authority record by specific fields.
 */
@Component
public class MarcAuthorityMatchEventHandler extends AbstractMarcMatchEventHandler {

  @Autowired
  public MarcAuthorityMatchEventHandler(RecordDao recordDao) {
    super(TypeConnection.MARC_AUTHORITY, recordDao, DI_SRS_MARC_AUTHORITY_RECORD_MATCHED,
      DI_SRS_MARC_AUTHORITY_RECORD_NOT_MATCHED);
  }

  @Override
  public boolean isPostProcessingNeeded() {
    return false;
  }

}
