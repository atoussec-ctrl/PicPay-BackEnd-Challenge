package com.atoussec.transfers.application.port.out;

import com.atoussec.transfers.domain.model.User;
import com.atoussec.transfers.domain.model.UserId;
import java.util.Optional;

public interface UserRepository {

  Optional<User> findById(UserId id);
}
