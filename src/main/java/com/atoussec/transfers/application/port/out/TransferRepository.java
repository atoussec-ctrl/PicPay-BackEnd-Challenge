package com.atoussec.transfers.application.port.out;

import com.atoussec.transfers.domain.model.Transfer;

public interface TransferRepository {

  void save(Transfer transfer);
}
