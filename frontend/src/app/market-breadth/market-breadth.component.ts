import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { catchError, finalize, forkJoin, of } from 'rxjs';
import { BreadthChartSeries, BreadthLineChartComponent } from './breadth-line-chart.component';
import {
  BreadthAlert,
  BreadthAlertType,
  BreadthRegime,
  BreadthScoreConfiguration,
  BreadthSnapshot,
  BreadthUniverse,
  IndexReferenceDataIssue,
  SectorBreadth,
} from './market-breadth.models';
import { MarketBreadthService } from './market-breadth.service';

interface ReferenceDataRow {
  symbol: string;
  ok: boolean;
  issue?: IndexReferenceDataIssue;
}
interface IndicatorCard {
  label: string;
  value: string;
  detail: string;
  tone: 'positive' | 'neutral' | 'negative';
}
interface ScoreRow {
  key: string;
  label: string;
  points: number;
  weight: number;
  reason: string;
}
interface LoadedFilters {
  universe: BreadthUniverse;
  from: string;
  to: string;
}

const REQUIRED_INDICES = [
  'NIFTY 50',
  'NIFTY 500',
  'NIFTY 200',
  'NIFTY BANK',
  'NIFTY IT',
  'NIFTY AUTO',
  'NIFTY PHARMA',
  'NIFTY FMCG',
  'NIFTY METAL',
  'NIFTY REALTY',
  'NIFTY ENERGY',
  'NIFTY FIN SERVICE',
  'NIFTY MEDIA',
  'NIFTY PSU BANK',
  'INDIA VIX',
];
const ALERT_TYPES: BreadthAlertType[] = [
  'BREADTH_50D_CROSS_50_UP',
  'BREADTH_50D_CROSS_50_DOWN',
  'BREADTH_50D_CROSS_60_UP',
  'BREADTH_50D_CROSS_60_DOWN',
  'BREADTH_50D_CROSS_70_UP',
  'BREADTH_50D_CROSS_70_DOWN',
  'BREADTH_200D_CROSS_UP',
  'BREADTH_200D_CROSS_DOWN',
  'BEARISH_AD_DIVERGENCE',
  'ZWEIG_BREADTH_THRUST',
  'MCCLELLAN_ZERO_CROSS_UP',
  'MCCLELLAN_ZERO_CROSS_DOWN',
  'SUMMATION_TREND_FLIP_UP',
  'SUMMATION_TREND_FLIP_DOWN',
  'NET_NEW_HIGHS_NEGATIVE_FLIP',
  'SECTOR_BREADTH_NARROWING',
  'INDIA_VIX_SPIKE',
  'INDIA_VIX_CROSS_ABOVE_EMA',
  'INDIA_VIX_CROSS_BELOW_EMA',
];

@Component({
  selector: 'app-market-breadth',
  imports: [CommonModule, BreadthLineChartComponent],
  templateUrl: './market-breadth.component.html',
  styleUrl: './market-breadth.component.scss',
})
export class MarketBreadthComponent implements OnInit {
  private readonly service = inject(MarketBreadthService);

  readonly universes: Array<{ value: BreadthUniverse; label: string }> = [
    { value: 'NIFTY500', label: 'NIFTY 500' },
    { value: 'NIFTY200', label: 'NIFTY 200' },
    { value: 'NIFTY50', label: 'NIFTY 50' },
  ];
  readonly alertTypes = ALERT_TYPES;
  readonly selectedUniverse = signal<BreadthUniverse>('NIFTY500');
  readonly fromDate = signal(this.dateOffset(-100));
  readonly toDate = signal(this.dateOffset(0));
  readonly loading = signal(false);
  readonly dashboardError = signal<string | null>(null);
  readonly latest = signal<BreadthSnapshot | null>(null);
  readonly history = signal<BreadthSnapshot[]>([]);
  readonly sectors = signal<SectorBreadth | null>(null);
  readonly configuration = signal<BreadthScoreConfiguration | null>(null);
  readonly recalculating = signal(false);
  readonly exporting = signal(false);
  readonly loadedFilters = signal<LoadedFilters | null>(null);
  readonly recalculationMessage = signal<string | null>(null);
  readonly alerts = signal<BreadthAlert[]>([]);
  readonly alertsLoading = signal(false);
  readonly alertsLoaded = signal(false);
  readonly alertsError = signal<string | null>(null);
  readonly alertType = signal<BreadthAlertType | ''>('');
  readonly referenceDataLoading = signal(false);
  readonly referenceDataLoaded = signal(false);
  readonly referenceDataError = signal<string | null>(null);
  readonly referenceDataIssues = signal<IndexReferenceDataIssue[]>([]);

  readonly stale = computed(() => {
    const latest = this.latest();
    const filters = this.loadedFilters();
    return !!latest && !!filters && this.calendarDaysBetween(latest.tradingDate, filters.to) > 4;
  });
  readonly dateRangeError = computed(() => {
    if (!this.fromDate() || !this.toDate()) return 'Both From and To dates are required.';
    return this.fromDate() > this.toDate() ? 'From date must be on or before the To date.' : null;
  });
  readonly dateRangeInvalid = computed(() => this.dateRangeError() !== null);
  readonly referenceDataStatus = computed(() => {
    if (this.referenceDataLoading()) return 'Checking indices…';
    if (this.referenceDataError()) return 'Reference data unavailable';
    if (!this.referenceDataLoaded()) return 'Reference data not checked';
    return `${this.referenceDataReadyCount()} of ${REQUIRED_INDICES.length} indices ready`;
  });
  readonly referenceDataRows = computed<ReferenceDataRow[]>(() => {
    const issues = new Map(this.referenceDataIssues().map((issue) => [issue.symbol, issue]));
    return REQUIRED_INDICES.map((symbol) =>
      issues.has(symbol) ? { symbol, ok: false, issue: issues.get(symbol) } : { symbol, ok: true },
    );
  });
  readonly referenceDataReadyCount = computed(
    () => this.referenceDataRows().filter((row) => row.ok).length,
  );

  readonly indicatorCards = computed<IndicatorCard[]>(() => {
    const snapshot = this.latest();
    if (!snapshot) return [];
    const m = snapshot.indicators;
    return [
      this.percentCard('Above 50-day EMA', m['BREADTH_50D_PERCENT'], 'Medium-term participation'),
      this.percentCard('Above 200-day EMA', m['BREADTH_200D_PERCENT'], 'Long-term participation'),
      this.numberCard(
        'McClellan Oscillator',
        m['MCCLELLAN_OSCILLATOR'],
        'Short-term breadth momentum',
      ),
      this.numberCard(
        'McClellan Summation',
        m['MCCLELLAN_SUMMATION'],
        m['SUMMATION_TREND'] > 0 ? 'Rising trend' : 'Flat or falling trend',
      ),
      this.numberCard('Net new highs', m['NET_NEW_HIGHS'], '52-week highs minus lows'),
      this.numberCard('TRIN', m['TRIN'], 'Below 1 favours advancing volume', true),
      this.percentCard(
        'Bullish-percent proxy',
        m['BULLISH_PERCENT_PROXY'],
        'Point-and-figure participation',
      ),
      this.numberCard(
        'India VIX',
        m['INDIA_VIX_CLOSE'],
        m['INDIA_VIX_ABOVE_EMA'] > 0.5 ? 'Above 20-day EMA' : 'At or below 20-day EMA',
        true,
      ),
      this.numberCard(
        'Advance / decline line',
        m['AD_LINE'],
        m['AD_LINE_RISING'] > 0.5 ? 'Rising' : 'Not rising',
      ),
    ];
  });

  readonly scoreRows = computed<ScoreRow[]>(() => {
    const snapshot = this.latest();
    const config = this.configuration();
    if (!snapshot || !config) return [];
    return Object.entries(config.weights).map(([key, weight]) => ({
      key,
      label: this.componentLabel(key),
      points: snapshot.componentScores[key] ?? 0,
      weight,
      reason: snapshot.componentReasons[key] ?? 'No explanation recorded.',
    }));
  });

  readonly benchmarkAdSeries = computed<BreadthChartSeries[]>(() => [
    this.series('NIFTY close', '#16765a', 'BENCHMARK_CLOSE'),
    this.series('A/D line', '#d18c24', 'AD_LINE'),
  ]);
  readonly movingAverageSeries = computed<BreadthChartSeries[]>(() => [
    this.series('Above 50-day EMA', '#16765a', 'BREADTH_50D_PERCENT'),
    this.series('Above 200-day EMA', '#5269b4', 'BREADTH_200D_PERCENT'),
  ]);
  readonly mcclellanSeries = computed<BreadthChartSeries[]>(() => [
    this.series('Oscillator', '#16765a', 'MCCLELLAN_OSCILLATOR'),
    this.series('Summation index', '#d18c24', 'MCCLELLAN_SUMMATION'),
  ]);
  readonly highLowSeries = computed<BreadthChartSeries[]>(() => [
    this.series('New highs', '#16815e', 'NEW_HIGHS'),
    this.series('New lows', '#c25252', 'NEW_LOWS'),
  ]);
  readonly chartLabels = computed(() => this.history().map((item) => item.tradingDate));

  ngOnInit(): void {
    this.refreshDashboard();
    this.loadReferenceData();
  }

  refreshDashboard(): void {
    if (this.dateRangeInvalid()) {
      this.dashboardError.set(this.dateRangeError());
      return;
    }
    this.loading.set(true);
    this.dashboardError.set(null);
    const universe = this.selectedUniverse();
    const from = this.fromDate();
    const to = this.toDate();
    forkJoin({
      history: this.service.getHistory(universe, from, to),
      sectors: this.service.getSectors(universe, to).pipe(catchError(() => of(null))),
      configuration: this.service.getConfiguration(),
    })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (data) => {
          this.history.set(data.history);
          const selectedSnapshot = data.history.at(-1) ?? null;
          this.latest.set(selectedSnapshot);
          this.sectors.set(
            selectedSnapshot?.tradingDate === data.sectors?.tradingDate ? data.sectors : null,
          );
          this.configuration.set(data.configuration);
          this.loadedFilters.set({ universe, from, to });
          this.loadAlerts();
        },
        error: (error) =>
          this.dashboardError.set(
            this.errorMessage(error, 'Market breadth dashboard could not be loaded.'),
          ),
      });
  }

  exportExcel(): void {
    const filters = this.loadedFilters();
    if (!filters || !this.history().length) return;
    this.exporting.set(true);
    this.dashboardError.set(null);
    this.service
      .exportExcel(filters.universe, filters.from, filters.to)
      .pipe(finalize(() => this.exporting.set(false)))
      .subscribe({
        next: (workbook) => {
          const url = URL.createObjectURL(workbook);
          const link = document.createElement('a');
          link.href = url;
          const actualFrom = this.history()[0]?.tradingDate ?? filters.from;
          const actualTo = this.history().at(-1)?.tradingDate ?? filters.to;
          link.download = `market-breadth-${filters.universe.toLowerCase()}-${actualFrom}-to-${actualTo}.xlsx`;
          link.click();
          link.remove();
          URL.revokeObjectURL(url);
        },
        error: (error) =>
          this.dashboardError.set(
            this.errorMessage(error, 'Market Breadth Excel export could not be generated.'),
          ),
      });
  }

  recalculate(): void {
    if (this.dateRangeInvalid()) return;
    this.recalculating.set(true);
    this.dashboardError.set(null);
    this.recalculationMessage.set(null);
    this.service
      .recalculate({
        universe: this.selectedUniverse(),
        fromDate: this.fromDate(),
        toDate: this.toDate(),
        methodology: 'CURRENT_CONSTITUENTS',
      })
      .pipe(finalize(() => this.recalculating.set(false)))
      .subscribe({
        next: (result) => {
          this.recalculationMessage.set(
            `Calculated ${result.calculatedCount} session(s); ${result.failureCount} failed.`,
          );
          this.refreshDashboard();
        },
        error: (error) =>
          this.dashboardError.set(this.errorMessage(error, 'Recalculation failed.')),
      });
  }

  loadAlerts(): void {
    const filters = this.loadedFilters() ?? {
      universe: this.selectedUniverse(),
      from: this.fromDate(),
      to: this.toDate(),
    };
    this.alertsLoading.set(true);
    this.alertsError.set(null);
    this.service
      .getAlerts({
        universe: filters.universe,
        alertType: this.alertType() || undefined,
        from: filters.from,
        to: filters.to,
        limit: 100,
      })
      .pipe(finalize(() => this.alertsLoading.set(false)))
      .subscribe({
        next: (alerts) => {
          this.alerts.set(alerts);
          this.alertsLoaded.set(true);
        },
        error: (error) =>
          this.alertsError.set(this.errorMessage(error, 'Alert history could not be loaded.')),
      });
  }

  loadReferenceData(): void {
    this.referenceDataLoading.set(true);
    this.referenceDataError.set(null);
    this.service
      .checkReferenceData()
      .pipe(finalize(() => this.referenceDataLoading.set(false)))
      .subscribe({
        next: (report) => {
          this.referenceDataIssues.set(report.issues);
          this.referenceDataLoaded.set(true);
        },
        error: (error) =>
          this.referenceDataError.set(
            this.errorMessage(error, 'Reference data status could not be loaded.'),
          ),
      });
  }

  setUniverse(event: Event): void {
    this.selectedUniverse.set((event.target as HTMLSelectElement).value as BreadthUniverse);
  }
  setFromDate(event: Event): void {
    this.fromDate.set((event.target as HTMLInputElement).value);
  }
  setToDate(event: Event): void {
    this.toDate.set((event.target as HTMLInputElement).value);
  }
  setAlertType(event: Event): void {
    this.alertType.set((event.target as HTMLSelectElement).value as BreadthAlertType | '');
  }
  regimeMessage(regime: BreadthRegime): string {
    if (regime === 'GREEN') return 'Broad participation supports momentum exposure.';
    if (regime === 'AMBER')
      return 'Participation is mixed; use selective exposure and tighter risk controls.';
    return 'Weak participation argues for defensive positioning.';
  }
  alertTypeLabel(type: BreadthAlertType): string {
    return this.titleCase(type);
  }
  componentLabel(key: string): string {
    return this.titleCase(key.replace('50D', '50-day').replace('200D', '200-day'));
  }
  issueTypeLabel(type: IndexReferenceDataIssue['issueType']): string {
    return (
      {
        MISSING_INSTRUMENT: 'Missing index',
        MISSING_INSTRUMENT_KEY: 'Missing instrument key',
        MISSING_HISTORY: 'No price history',
        STALE_HISTORY: 'Stale price history',
      } as const
    )[type];
  }
  universeLabel(value: BreadthUniverse): string {
    return this.universes.find((item) => item.value === value)?.label ?? value;
  }

  private series(label: string, color: string, key: string): BreadthChartSeries {
    return { label, color, values: this.history().map((item) => item.indicators[key] ?? null) };
  }
  private percentCard(label: string, value: number | undefined, detail: string): IndicatorCard {
    const numeric = value ?? 0;
    return {
      label,
      value: `${numeric.toFixed(1)}%`,
      detail,
      tone: numeric >= 55 ? 'positive' : numeric < 40 ? 'negative' : 'neutral',
    };
  }
  private numberCard(
    label: string,
    value: number | undefined,
    detail: string,
    invert = false,
  ): IndicatorCard {
    const numeric = value ?? 0;
    const positive = invert ? numeric > 0 && numeric < 1 : numeric > 0;
    return {
      label,
      value: numeric.toLocaleString('en-IN', { maximumFractionDigits: 2 }),
      detail,
      tone: positive ? 'positive' : numeric === 0 ? 'neutral' : 'negative',
    };
  }
  private titleCase(value: string): string {
    return value
      .toLowerCase()
      .split('_')
      .map((word) => (word ? word[0].toUpperCase() + word.slice(1) : word))
      .join(' ');
  }
  private dateOffset(days: number): string {
    const date = new Date();
    date.setDate(date.getDate() + days);
    return [
      date.getFullYear(),
      String(date.getMonth() + 1).padStart(2, '0'),
      String(date.getDate()).padStart(2, '0'),
    ].join('-');
  }
  private calendarDaysBetween(from: string, to: string): number {
    return Math.floor(
      (new Date(`${to}T00:00:00`).getTime() - new Date(`${from}T00:00:00`).getTime()) / 86_400_000,
    );
  }
  private errorMessage(error: any, fallback: string): string {
    return error?.error?.detail ?? error?.error?.message ?? error?.message ?? fallback;
  }
}
