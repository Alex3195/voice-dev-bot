package com.alex.voicedevbot.adapter.out.persistence;

import com.alex.voicedevbot.application.port.out.StorageException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;

/** Oddiy JDBC ustidan yupqa qatlam: ulanish, tranzaksiya va xatoni port exception'iga o'girish. */
final class Jdbc {

  @FunctionalInterface
  interface Work<T> {
    T run(Connection connection) throws SQLException;
  }

  private final DataSource dataSource;

  Jdbc(DataSource dataSource) {
    this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
  }

  <T> T query(String description, Work<T> work) {
    try (Connection connection = dataSource.getConnection()) {
      return work.run(connection);
    } catch (SQLException e) {
      throw new StorageException("Failed to " + description, e);
    }
  }

  <T> T inTransaction(String description, Work<T> work) {
    return query(
        description,
        connection -> {
          connection.setAutoCommit(false);
          try {
            T result = work.run(connection);
            connection.commit();
            return result;
          } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
          }
        });
  }
}
