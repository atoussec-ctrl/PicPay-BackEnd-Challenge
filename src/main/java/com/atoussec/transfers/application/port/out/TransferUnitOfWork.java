package com.atoussec.transfers.application.port.out;

import com.atoussec.transfers.domain.model.Transfer;
import com.atoussec.transfers.domain.model.TransferCommand;
import com.atoussec.transfers.domain.model.TransferId;
import java.time.Instant;

public interface TransferUnitOfWork {

  Transfer execute(TransferId transferId, TransferCommand command, Instant occurredAt);
}
