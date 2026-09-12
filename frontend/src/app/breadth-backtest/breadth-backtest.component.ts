import { CurrencyPipe, DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import {
  BreadthBacktestRunSummary,
  BreadthBacktestService,
  BreadthEntryMode,
  BreadthFilteredMomentumBacktestResult,
  BreadthThresholdSensitivityResult,
  ForwardReturnAnalysisResult,
  NiftyIndexName,
  SensitivityMaBasis,
} from './breadth-backtest.service';

@Component({
  selector: 'app-breadth-backtest',
  imports: [FormsModule, CurrencyPipe, DatePipe, DecimalPipe, PercentPipe],
  templateUrl: './breadth-backtest.component.html',
  styleUrls: [
    '../momentum-backtest/momentum-backtest.component.scss',
    '../momentum-backtest/momentum-backtest-results.scss',
    '../momentum-backtest/momentum-backtest-toggle.scss',
  ],
})
export class BreadthBacktestComponent implements OnInit {
  private readonly service = inject(BreadthBacktestService);

  readonly tab = signal<'backtest' | 'forward-returns' | 'sensitivity'>('backtest');
  readonly universeOptions: NiftyIndexName[] = [
    'NIFTY50',
    'NIFTY200',
    'NIFTY500',
    'NIFTY750',
    'NIFTY_NEXT50',
    'NIFTY_MIDCAP150',
    'NIFTY_SMALLCAP250',
  ];
  readonly entryModeOptions: BreadthEntryMode[] = [
    'BASELINE',
    'GREEN_ONLY',
    'GREEN_AMBER',
    'SCORE_SCALED',
  ];
  readonly maBasisOptions: SensitivityMaBasis[] = ['PERSISTED_BREADTH_SCORE', 'LEGACY_SMA_OVERLAY'];

  readonly loading = signal(false);
  readonly error = signal('');
  readonly result = signal<BreadthFilteredMomentumBacktestResult | null>(null);
  readonly currentRunId = signal<string | null>(null);
  readonly executions = signal<BreadthBacktestRunSummary[]>([]);
  readonly setup = {
    startDate: '2021-09-01',
    endDate: new Date().toISOString().slice(0, 10),
    initialCapital: 1000000,
    entryRank: 20,
    retentionRank: 40,
    benchmark: 'NIFTY 500',
    transactionCostPercent: 0.25,
    slippagePercent: 0.1,
    riskFreeRatePercent: 6,
    rebalanceMode: 'EQUAL_WEIGHT',
    bufferAmount: 200000,
    maximumLeverageAmount: 0,
    borrowingInterestRatePercent: 10,
    stopModel: 'ATR',
    trailingStopPercent: 0,
    atrPeriod: 14,
    atrMultiplier: 3,
    cooldownWeeks: 2,
    benchmarkSmaPeriod: 200,
    breadthThresholdPercent: 20,
    weakExposureCapPercent: 50,
    breadthUniverse: 'NIFTY500' as NiftyIndexName,
    breadthEntryMode: 'GREEN_ONLY' as BreadthEntryMode,
    breadthScoreCutoff: 50,
  };

  readonly sLoading = signal(false);
  readonly sError = signal('');
  readonly sResult = signal<BreadthThresholdSensitivityResult | null>(null);
  readonly sCurrentRunId = signal<string | null>(null);
  readonly sExecutions = signal<BreadthBacktestRunSummary[]>([]);
  readonly sSetup = {
    ...this.setup,
    universes: ['NIFTY500'] as NiftyIndexName[],
    entryModes: ['GREEN_ONLY', 'GREEN_AMBER'] as BreadthEntryMode[],
    scoreCutoffsText: '20,30,40,50',
    movingAverageBasesToCompare: ['PERSISTED_BREADTH_SCORE'] as SensitivityMaBasis[],
  };

  readonly fLoading = signal(false);
  readonly fError = signal('');
  readonly fResult = signal<ForwardReturnAnalysisResult | null>(null);
  readonly fSetup = {
    universe: 'NIFTY500' as NiftyIndexName,
    from: '2021-09-01',
    to: new Date().toISOString().slice(0, 10),
    benchmark: 'NIFTY 500',
  };

  ngOnInit(): void {
    this.refreshExecutions();
    this.refreshSensitivityExecutions();
  }

  run(): void {
    this.loading.set(true);
    this.error.set('');
    this.service
      .run(this.setup)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (value) => {
          this.result.set(value.result);
          this.currentRunId.set(value.runId);
          this.refreshExecutions();
        },
        error: (error) =>
          this.error.set(
            error?.error?.message || error?.error?.detail || 'Breadth-filtered backtest failed.',
          ),
      });
  }

  loadExecution(runId: string): void {
    if (!runId) return;
    this.loading.set(true);
    this.error.set('');
    this.service
      .execution(runId)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (value) => {
          this.result.set(value.backtestResult);
          this.currentRunId.set(runId);
        },
        error: (error) =>
          this.error.set(
            error?.error?.message || error?.error?.detail || 'Could not load the saved run.',
          ),
      });
  }

  refreshExecutions(): void {
    this.service
      .executions('BACKTEST')
      .subscribe({ next: (value) => this.executions.set(value), error: () => this.executions.set([]) });
  }

  runSensitivity(): void {
    const scoreCutoffs = this.sSetup.scoreCutoffsText
      .split(',')
      .map((value) => Number(value.trim()))
      .filter((value) => !Number.isNaN(value));
    if (!scoreCutoffs.length) {
      this.sError.set('Enter at least one score cutoff.');
      return;
    }
    this.sLoading.set(true);
    this.sError.set('');
    const { scoreCutoffsText, ...rest } = this.sSetup;
    this.service
      .thresholdSensitivity({ ...rest, scoreCutoffs })
      .pipe(finalize(() => this.sLoading.set(false)))
      .subscribe({
        next: (value) => {
          this.sResult.set(value.result);
          this.sCurrentRunId.set(value.runId);
          this.refreshSensitivityExecutions();
        },
        error: (error) =>
          this.sError.set(
            error?.error?.message || error?.error?.detail || 'Threshold sensitivity run failed.',
          ),
      });
  }

  loadSensitivityExecution(runId: string): void {
    if (!runId) return;
    this.sLoading.set(true);
    this.sError.set('');
    this.service
      .execution(runId)
      .pipe(finalize(() => this.sLoading.set(false)))
      .subscribe({
        next: (value) => {
          this.sResult.set(value.sensitivityResult);
          this.sCurrentRunId.set(runId);
        },
        error: (error) =>
          this.sError.set(
            error?.error?.message || error?.error?.detail || 'Could not load the saved run.',
          ),
      });
  }

  refreshSensitivityExecutions(): void {
    this.service.executions('SENSITIVITY').subscribe({
      next: (value) => this.sExecutions.set(value),
      error: () => this.sExecutions.set([]),
    });
  }

  runForwardReturnAnalysis(): void {
    this.fLoading.set(true);
    this.fError.set('');
    this.service
      .forwardReturnAnalysis(
        this.fSetup.universe,
        this.fSetup.from,
        this.fSetup.to,
        this.fSetup.benchmark,
      )
      .pipe(finalize(() => this.fLoading.set(false)))
      .subscribe({
        next: (value) => this.fResult.set(value),
        error: (error) =>
          this.fError.set(
            error?.error?.message || error?.error?.detail || 'Forward-return analysis failed.',
          ),
      });
  }
}
