package org.factor_investing.quant_strategy.strategies.momentum;

import org.factor_investing.quant_strategy.model.MomentumBacktestRun;
import org.factor_investing.quant_strategy.model.response.MomentumBacktestResult;
import org.factor_investing.quant_strategy.repository.MomentumBacktestRunRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MomentumBacktestHistoryServiceTest {
    private final MomentumBacktestRunRepository repository=mock(MomentumBacktestRunRepository.class);
    private final MomentumBacktestHistoryService service=new MomentumBacktestHistoryService(repository);

    @Test
    void savesAndReloadsCompletedResult() {
        UUID id=UUID.randomUUID(); MomentumBacktestResult result=mock(MomentumBacktestResult.class);
        when(result.startDate()).thenReturn(LocalDate.of(2021,1,1)); when(result.endDate()).thenReturn(LocalDate.of(2025,1,1));
        when(result.benchmark()).thenReturn("NIFTY 500"); when(result.rebalanceMode()).thenReturn("REPLACEMENT_ONLY");
        when(repository.save(any())).thenAnswer(invocation->{MomentumBacktestRun run=invocation.getArgument(0);run.setId(id);return run;});

        assertThat(service.save(result,15,40,.1,.1,0,0)).isEqualTo(id);
        verify(repository).save(argThat(run->run.getResult()==result&&run.getEntryRank()==15&&run.getRetentionRank()==40));

        MomentumBacktestRun stored=new MomentumBacktestRun();stored.setResult(result);when(repository.findById(id)).thenReturn(Optional.of(stored));
        assertThat(service.get(id)).isSameAs(result);
    }
}
