import {DatePipe, DecimalPipe} from '@angular/common';
import {Component, EventEmitter, OnInit, Output, computed, inject, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {finalize} from 'rxjs';
import {AssetDataType, MomentumAnalysisService, MomentumAsset, MomentumExecution, MomentumResult} from '../momentum-analysis/momentum-analysis.service';

@Component({
  selector: 'app-momentum-dashboard',
  imports: [FormsModule, DecimalPipe, DatePipe],
  templateUrl: './momentum-dashboard.component.html',
  styleUrls: ['./momentum-dashboard.component.scss', './momentum-dashboard-history.scss']
})
export class MomentumDashboardComponent implements OnInit {
  private readonly service = inject(MomentumAnalysisService);
  @Output() readonly openAnalysis = new EventEmitter<void>();

  readonly today = this.localDate(new Date());
  readonly loading = signal(false);
  readonly historyLoading = signal(false);
  readonly result = signal<MomentumResult | null>(null);
  readonly executions = signal<MomentumExecution[]>([]);
  readonly error = signal<string | null>(null);
  readonly loadedFromHistory = signal(false);
  assetType: AssetDataType = 'STOCK';
  asOfDate = this.today;

  readonly rankedAssets = computed(() => this.addRanks(this.result()?.allStocks ?? []));
  readonly leaders = computed(() => [...this.rankedAssets()].sort((a, b) => (a.totalRankScore ?? 0) - (b.totalRankScore ?? 0)).slice(0, 10));
  readonly topFive = computed(() => this.leaders().slice(0, 5));
  readonly medianOneYear = computed(() => this.median(this.rankedAssets().map(asset => asset.oneYearReturn)));
  readonly positive12 = computed(() => this.positivePercentage('oneYearReturn'));
  readonly positive6 = computed(() => this.positivePercentage('sixMonthReturn'));
  readonly positive3 = computed(() => this.positivePercentage('threeMonthReturn'));
  readonly stockExecutions = computed(() => this.executions().filter(item => item.assetDataType === 'STOCK'));
  readonly etfExecutions = computed(() => this.executions().filter(item => item.assetDataType === 'ETF'));
  readonly indexExecutions = computed(() => this.executions().filter(item => item.assetDataType === 'INDEX'));

  ngOnInit(): void { this.loadHistory(true); }

  assetTypeChanged(type: AssetDataType): void {
    this.assetType = type;
    this.result.set(null);
    this.error.set(null);
    this.loadLatestForType();
  }

  loadExecution(execution: MomentumExecution): void {
    this.loading.set(true);
    this.error.set(null);
    this.assetType = execution.assetDataType;
    this.asOfDate = execution.strategyRunDate;
    this.service.savedResults(execution.assetDataType, execution.strategyRunDate).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: items => {
        const assets: MomentumAsset[] = items.map(item => ({...item, qualifiesForMomentum: true}));
        this.result.set({allStocks: assets, qualifiedStocks: assets, topStockNames: assets.slice(0, 10).map(item => item.stockName), totalAnalyzed: assets.length, qualifiedCount: assets.length, valid: true, message: `Loaded saved ${execution.assetDataType.toLowerCase()} momentum results for ${execution.strategyRunDate}`});
        this.loadedFromHistory.set(true);
      },
      error: error => this.error.set(error?.error?.message || 'The saved momentum execution could not be loaded.')
    });
  }

  barWidth(value: number): number { return Math.max(3, Math.min(100, Math.abs(value))); }
  assetLabel(type: AssetDataType): string { return type === 'STOCK' ? 'Stocks' : type === 'ETF' ? 'ETFs' : 'Indices'; }

  private loadHistory(loadLatest: boolean): void {
    this.historyLoading.set(true);
    this.service.executions().pipe(finalize(() => this.historyLoading.set(false))).subscribe({
      next: value => {
        this.executions.set(value);
        if (loadLatest) this.loadLatestForType();
      },
      error: () => this.error.set('Saved momentum executions could not be loaded.')
    });
  }

  private loadLatestForType(): void {
    const latest = this.executions().find(execution => execution.assetDataType === this.assetType);
    if (latest) this.loadExecution(latest);
  }

  private positivePercentage(field: 'oneYearReturn' | 'sixMonthReturn' | 'threeMonthReturn'): number {
    const assets = this.rankedAssets();
    return assets.length ? assets.filter(asset => asset[field] > 0).length * 100 / assets.length : 0;
  }

  private median(values: number[]): number {
    if (!values.length) return 0;
    const sorted = [...values].sort((a, b) => a - b);
    const middle = Math.floor(sorted.length / 2);
    return sorted.length % 2 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
  }

  private addRanks(source: MomentumAsset[]): MomentumAsset[] {
    const rank = (field: keyof MomentumAsset) => new Map([...source].sort((a, b) => Number(b[field]) - Number(a[field])).map((item, index) => [item.stockName, index + 1]));
    const rank12 = rank('oneYearReturn'), rank6 = rank('sixMonthReturn'), rank3 = rank('threeMonthReturn');
    return source.map(item => {
      const r12 = item.rank12Months ?? rank12.get(item.stockName) ?? 0;
      const r6 = item.rank6Months ?? rank6.get(item.stockName) ?? 0;
      const r3 = item.rank3Months ?? rank3.get(item.stockName) ?? 0;
      return {...item, rank12Months: r12, rank6Months: r6, rank3Months: r3, totalRankScore: item.totalRankScore ?? (r12 + r6 * 2 + r3 * 3)};
    });
  }

  private localDate(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }
}
