package org.factor_investing.quant_strategy.service;

import org.factor_investing.quant_strategy.model.AssetDataType;
import org.factor_investing.quant_strategy.model.PriceFrequencey;
import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PriceImportPersistenceTest {
    @Test
    void holdsTransactionLockUntilRollbackAndDoesNotUseSessionUnlock() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        Statement heartbeat = mock(Statement.class);
        ResultSet result = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("select pg_try_advisory_xact_lock(173912, ?)")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getBoolean(1)).thenReturn(true);
        when(connection.createStatement()).thenReturn(heartbeat);
        var service = new PriceImportPersistence(null, null, source);
        try (var lock = service.acquireLock(AssetDataType.STOCK, PriceFrequencey.DAILY)) {
            lock.checkHeld();
            verify(connection, never()).rollback();
        }
        var order = inOrder(connection, statement, heartbeat);
        order.verify(connection).setAutoCommit(false);
        order.verify(connection).prepareStatement("select pg_try_advisory_xact_lock(173912, ?)");
        order.verify(statement).setInt(eq(1), anyInt());
        order.verify(statement).executeQuery();
        order.verify(statement).close();
        order.verify(connection).createStatement();
        order.verify(heartbeat).execute("select 1");
        order.verify(heartbeat).close();
        order.verify(connection).rollback();
        order.verify(connection).close();
        verify(connection, never()).commit();
    }

    @Test
    void lostLockStopsImportAndFailedRollbackAbortsConnection() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getBoolean(1)).thenReturn(true);
        var lock = new PriceImportPersistence(null, null, source).acquireLock(AssetDataType.ETF, PriceFrequencey.DAILY);
        when(connection.createStatement()).thenThrow(new SQLException("connection lost"));
        assertThatThrownBy(lock::checkHeld).hasMessageContaining("lock connection lost");
        doThrow(new SQLException("connection lost")).when(connection).rollback();
        assertThatThrownBy(lock::close).hasMessageContaining("Cannot release");
        verify(connection).abort(any());
        verify(connection).close();
    }
}
