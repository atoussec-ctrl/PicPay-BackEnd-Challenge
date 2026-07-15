package com.atoussec.transfers.application.port.out;

import com.atoussec.transfers.domain.model.LedgerEntry;

public interface LedgerRepository {

  void save(LedgerEntry entry);
}
