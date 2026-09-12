export type BreadthUniverse = 'NIFTY50' | 'NIFTY200' | 'NIFTY500';
export type BreadthRegime = 'GREEN' | 'AMBER' | 'RED';
export type BreadthQuality = 'VALID' | 'WARNING' | 'INVALID';
export type BreadthMethodology = 'CURRENT_CONSTITUENTS' | 'POINT_IN_TIME_CONSTITUENTS';

export interface BreadthSnapshot {
  id: string;
  universe: BreadthUniverse;
  tradingDate: string;
  methodology: BreadthMethodology;
  scoreConfigurationVersion: number;
  coveragePercent: number;
  qualityStatus: BreadthQuality;
  score: number;
  regime: BreadthRegime;
  indicators: Record<string, number>;
  componentScores: Record<string, number>;
  componentReasons: Record<string, string>;
}

export interface SectorStatus {
  symbol: string;
  above50DayEma: boolean;
  distancePercent: number;
}

export interface SectorBreadth {
  universe: BreadthUniverse;
  tradingDate: string;
  participatingSectors: number;
  availableSectors: number;
  sectors: SectorStatus[];
}

export interface BreadthScoreConfiguration {
  id: string;
  version: number;
  weights: Record<string, number>;
  greenThreshold: number;
  amberThreshold: number;
  vixSpikePercent: number;
  risingFallingLookbackSessions: number;
  expansionContractionLookbackSessions: number;
  effectiveFrom: string;
  active: boolean;
}

export interface BreadthScoreConfigurationRequest {
  weights: Record<string, number>;
  greenThreshold: number;
  amberThreshold: number;
  vixSpikePercent: number;
  risingFallingLookbackSessions: number;
  expansionContractionLookbackSessions: number;
  effectiveFrom?: string;
}

export interface BreadthRecalculationRequest {
  fromDate: string;
  toDate: string;
  universe: BreadthUniverse;
  methodology: BreadthMethodology;
}

export interface BreadthRecalculationResponse extends BreadthRecalculationRequest {
  calculatedCount: number;
  failureCount: number;
}

export type IndexReferenceDataIssueType =
  'MISSING_INSTRUMENT' | 'MISSING_INSTRUMENT_KEY' | 'MISSING_HISTORY' | 'STALE_HISTORY';

export interface IndexReferenceDataIssue {
  symbol: string;
  issueType: IndexReferenceDataIssueType;
  detail: string;
}

export interface BreadthReferenceDataReport {
  issues: IndexReferenceDataIssue[];
}

export type AlertDeliveryState = 'PENDING' | 'SENT' | 'FAILED';
export type BreadthAlertType =
  | 'BREADTH_50D_CROSS_50_UP'
  | 'BREADTH_50D_CROSS_50_DOWN'
  | 'BREADTH_50D_CROSS_60_UP'
  | 'BREADTH_50D_CROSS_60_DOWN'
  | 'BREADTH_50D_CROSS_70_UP'
  | 'BREADTH_50D_CROSS_70_DOWN'
  | 'BREADTH_200D_CROSS_UP'
  | 'BREADTH_200D_CROSS_DOWN'
  | 'BEARISH_AD_DIVERGENCE'
  | 'ZWEIG_BREADTH_THRUST'
  | 'MCCLELLAN_ZERO_CROSS_UP'
  | 'MCCLELLAN_ZERO_CROSS_DOWN'
  | 'SUMMATION_TREND_FLIP_UP'
  | 'SUMMATION_TREND_FLIP_DOWN'
  | 'NET_NEW_HIGHS_NEGATIVE_FLIP'
  | 'SECTOR_BREADTH_NARROWING'
  | 'INDIA_VIX_SPIKE'
  | 'INDIA_VIX_CROSS_ABOVE_EMA'
  | 'INDIA_VIX_CROSS_BELOW_EMA';

export interface BreadthAlert {
  id: string;
  alertType: BreadthAlertType;
  universe: BreadthUniverse;
  triggerDate: string;
  triggerValues: Record<string, number>;
  message: string;
  deliveryState: AlertDeliveryState;
  createdAt: string;
}

export interface BreadthAlertFilters {
  universe?: BreadthUniverse;
  alertType?: BreadthAlertType;
  from?: string;
  to?: string;
  limit?: number;
}
