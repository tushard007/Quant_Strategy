package org.factor_investing.quant_strategy.service;

import org.factor_investing.quant_strategy.model.ETFPricesJson;
import org.factor_investing.quant_strategy.model.StockPricesJson;
import org.factor_investing.quant_strategy.repository.ETFPriceDataRepository;
import org.factor_investing.quant_strategy.repository.StockDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import org.factor_investing.quant_strategy.model.AssetDataType;
import org.factor_investing.quant_strategy.model.PriceFrequencey;

/** Separate proxy ensures each chunk is committed independently of the caller. */
@Service
public class PriceImportPersistence {
    private final DataSource dataSource;
    private final StockDataRepository stocks;
    private final ETFPriceDataRepository etfs;

    public PriceImportPersistence(StockDataRepository stocks, ETFPriceDataRepository etfs, DataSource dataSource) {
        this.dataSource = dataSource;
        this.stocks = stocks;
        this.etfs = etfs;
    }

    /** The lock transaction pins one backend even through transaction-mode poolers.
     * Price writes still commit independently on separate connections.
     */
    public ImportLock acquireLock(AssetDataType asset, PriceFrequencey timeFrame) {
        int key = (asset.name() + ":" + timeFrame.name()).hashCode();
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("select pg_try_advisory_xact_lock(173912, ?)")) {
                statement.setInt(1, key);
                try (var result = statement.executeQuery()) {
                    if (!result.next() || !result.getBoolean(1)) {
                        throw new IllegalStateException("An import is already running for " + asset + " " + timeFrame);
                    }
                }
            }
            return new ImportLock(connection);
        } catch (SQLException | RuntimeException exception) {
            if (connection != null) {
                try { new ImportLock(connection).close(); }
                catch (RuntimeException closeError) { exception.addSuppressed(closeError); }
            }
            throw new IllegalStateException("Cannot acquire price import lock", exception);
        }
    }

    public static final class ImportLock implements AutoCloseable {
        private final Connection connection;

        private ImportLock(Connection connection) {
            this.connection = connection;
        }

        /** Stop before another write if an idle timeout/failover lost the lock transaction. */
        public void checkHeld() {
            try (var statement = connection.createStatement()) {
                statement.execute("select 1");
            } catch (SQLException exception) {
                throw new IllegalStateException("Import lock connection lost; retry the import", exception);
            }
        }

        @Override
        public void close() {
            RuntimeException failure = null;
            try {
                // Transaction-scoped locks are released by rollback, with no session state left behind.
                connection.rollback();
            } catch (SQLException exception) {
                try { connection.abort(Runnable::run); }
                catch (SQLException abortError) { exception.addSuppressed(abortError); }
                failure = new IllegalStateException("Cannot release price import lock", exception);
            } finally {
                try { connection.close(); }
                catch (SQLException exception) {
                    if (failure == null) failure = new IllegalStateException("Cannot close import lock connection", exception);
                    else failure.addSuppressed(exception);
                }
            }
            if (failure != null) throw failure;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveStocks(List<StockPricesJson> rows) {
        stocks.saveAll(rows);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveEtfs(List<ETFPricesJson> rows) {
        etfs.saveAll(rows);
    }
}
