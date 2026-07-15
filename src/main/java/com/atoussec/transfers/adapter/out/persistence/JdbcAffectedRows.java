package com.atoussec.transfers.adapter.out.persistence;

final class JdbcAffectedRows {

  private JdbcAffectedRows() {}

  static void requireExactlyOne(int affectedRows, String operation) {
    if (affectedRows != 1) {
      throw new IllegalStateException(operation + " must affect exactly one row");
    }
  }
}
