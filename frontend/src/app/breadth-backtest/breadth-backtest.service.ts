import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import {
  RiskAdjustedMomentumBacktestResult,
} from '../risk-adjusted-momentum-backtest/risk-adjusted-momentum-backtest.service';

export type NiftyIndexName =
  | 'NIFTY50'
  | 'NIFTY500'
  | 'NIFTY750'
  | 'NIFTY_MIDCAP150'
  | 'NIFTY_NEXT50'
  | 'NIFTY_SMALLCAP250'
  | 'NIFTY200';
export type BreadthEntryMode = 'BASELINE' | 'GREEN_ONLY' | 'GREEN_AMBER' | 'SCORE_SCALED';
export type SensitivityMaBasis = 'PERSISTED_BREADTH_SCORE' | 'LEGACY_SMA_OVERLAY';
export type BreadthBacktestRunType = 'BACKTEST' | 'SENSITIVITY';
export type BreadthBacktestStatus = 'COMPLETED' | 'FAILED';
export type BreadthMethodology = 'CURRENT_CONSTITUENTS' | 'POINT_IN_TIME_CONSTITUENTS';

export interface BreadthFilteredMomentumBacktestRequest {
  startDate: string;
  endDate: string;
  initialCapital: number;
  entryRank: number;
  retentionRank: number;
  benchmark: string;
  transactionCostPercent: number;
  slippagePercent: number;
  riskFreeRatePercent: number;
  rebalanceMode: string;
  bufferAmount: number;
  maximumLeverageAmount: number;
  borrowingInterestRatePercent: number;
  stopModel: string;
  trailingStopPercent: number;
  atrPeriod: number;
  atrMultiplier: number;
  cooldownWeeks: number;
  benchmarkSmaPeriod: number;
  breadthThresholdPercent: number;
  weakExposureCapPercent: number;
  breadthUniverse: NiftyIndexName;
  breadthEntryMode: BreadthEntryMode;
  breadthScoreCutoff: number;
}

export interface BreadthCoverageNote {
  signalDate: string;
  reason: string;
}

export interface BreadthFilteredMomentumBacktestResult {
  baseline: RiskAdjustedMomentumBacktestResult;
  breadthFiltered: RiskAdjustedMomentumBacktestResult;
  breadthUniverse: NiftyIndexName;
  breadthEntryMode: BreadthEntryMode;
  breadthScoreCutoff: number;
  methodology: BreadthMethodology;
  representativeScoreConfigurationVersion: number;
  scoreConfigurationVersionsObserved: number[];
  coverageNotes: BreadthCoverageNote[];
}

export interface BreadthThresholdSensitivityRequest {
  startDate: string;
  endDate: string;
  initialCapital: number;
  entryRank: number;
  retentionRank: number;
  benchmark: string;
  transactionCostPercent: number;
  slippagePercent: number;
  riskFreeRatePercent: number;
  rebalanceMode: string;
  bufferAmount: number;
  maximumLeverageAmount: number;
  borrowingInterestRatePercent: number;
  stopModel: string;
  trailingStopPercent: number;
  atrPeriod: number;
  atrMultiplier: number;
  cooldownWeeks: number;
  benchmarkSmaPeriod: number;
  breadthThresholdPercent: number;
  weakExposureCapPercent: number;
  universes: NiftyIndexName[];
  entryModes: BreadthEntryMode[];
  scoreCutoffs: number[];
  movingAverageBasesToCompare: SensitivityMaBasis[];
}

export interface BreadthThresholdSensitivityCell {
  universe: NiftyIndexName;
  entryMode: BreadthEntryMode;
  scoreCutoff: number;
  maBasis: SensitivityMaBasis;
  baselineTotalReturn: number;
  baselineCagr: number;
  baselineSharpeRatio: number;
  baselineMaxDrawdown: number;
  filteredTotalReturn: number;
  filteredCagr: number;
  filteredSharpeRatio: number;
  filteredMaxDrawdown: number;
  qualifyingSnapshotSampleCount: number;
  rebalanceCount: number;
}

export interface BreadthThresholdSensitivityResult {
  request: BreadthThresholdSensitivityRequest;
  cells: BreadthThresholdSensitivityCell[];
  warnings: string[];
}

export interface ForwardReturnStatistics {
  groupKey: string;
  sampleCount: number;
  meanReturn20: number;
  medianReturn20: number;
  positiveRatePercent20: number;
  volatility20: number;
  worstReturn20: number;
  meanReturn60: number;
  medianReturn60: number;
  positiveRatePercent60: number;
  volatility60: number;
  worstReturn60: number;
}

export interface ForwardReturnAnalysisResult {
  universe: NiftyIndexName;
  from: string;
  to: string;
  benchmarkIndex: string;
  totalObservations: number;
  observationsMissingForward20: number;
  observationsMissingForward60: number;
  byRegime: ForwardReturnStatistics[];
  byScoreBucket: ForwardReturnStatistics[];
  byRegimeAndScoreBucket: ForwardReturnStatistics[];
}

export interface BreadthBacktestRunSummary {
  id: string;
  createdAt: string;
  runType: BreadthBacktestRunType;
  status: BreadthBacktestStatus;
  startDate: string;
  endDate: string;
  breadthUniverse: NiftyIndexName;
  breadthEntryMode: BreadthEntryMode;
  breadthScoreCutoff: number | null;
  baselineTotalReturn: number | null;
  filteredTotalReturn: number | null;
  filteredSharpeRatio: number | null;
  filteredMaximumDrawdown: number | null;
  sampleCount: number | null;
}

export interface BreadthBacktestRunDetail {
  id: string;
  createdAt: string;
  runType: BreadthBacktestRunType;
  status: BreadthBacktestStatus;
  backtestResult: BreadthFilteredMomentumBacktestResult | null;
  sensitivityResult: BreadthThresholdSensitivityResult | null;
}

export interface RunResponse<T> {
  result: T;
  runId: string | null;
}

@Injectable({ providedIn: 'root' })
export class BreadthBacktestService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/breadth-backtest';

  run(
    request: BreadthFilteredMomentumBacktestRequest,
  ): Observable<RunResponse<BreadthFilteredMomentumBacktestResult>> {
    return this.http
      .post<BreadthFilteredMomentumBacktestResult>(`${this.baseUrl}/run`, request, {
        observe: 'response',
      })
      .pipe(
        map((response) => ({
          result: response.body as BreadthFilteredMomentumBacktestResult,
          runId: response.headers.get('X-Backtest-Run-Id'),
        })),
      );
  }

  thresholdSensitivity(
    request: BreadthThresholdSensitivityRequest,
  ): Observable<RunResponse<BreadthThresholdSensitivityResult>> {
    return this.http
      .post<BreadthThresholdSensitivityResult>(`${this.baseUrl}/threshold-sensitivity`, request, {
        observe: 'response',
      })
      .pipe(
        map((response) => ({
          result: response.body as BreadthThresholdSensitivityResult,
          runId: response.headers.get('X-Backtest-Run-Id'),
        })),
      );
  }

  forwardReturnAnalysis(
    universe: NiftyIndexName,
    from: string,
    to: string,
    benchmark: string,
  ): Observable<ForwardReturnAnalysisResult> {
    const params = new HttpParams()
      .set('universe', universe)
      .set('from', from)
      .set('to', to)
      .set('benchmark', benchmark);
    return this.http.post<ForwardReturnAnalysisResult>(
      `${this.baseUrl}/forward-return-analysis`,
      null,
      { params },
    );
  }

  executions(runType?: BreadthBacktestRunType): Observable<BreadthBacktestRunSummary[]> {
    const params = runType ? new HttpParams().set('runType', runType) : undefined;
    return this.http.get<BreadthBacktestRunSummary[]>(`${this.baseUrl}/executions`, { params });
  }

  execution(runId: string): Observable<BreadthBacktestRunDetail> {
    return this.http.get<BreadthBacktestRunDetail>(`${this.baseUrl}/executions/${runId}`);
  }
}
