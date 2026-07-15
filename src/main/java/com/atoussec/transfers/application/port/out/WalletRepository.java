package com.atoussec.transfers.application.port.out;

import com.atoussec.transfers.domain.model.UserId;
import com.atoussec.transfers.domain.model.Wallet;
import java.util.List;
import java.util.Optional;

public interface WalletRepository {

  Optional<Wallet> findByOwnerId(UserId ownerId);

  List<Wallet> lockByOwnerIds(UserId firstOwnerId, UserId secondOwnerId);
}
