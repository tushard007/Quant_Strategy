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
  readonly chartRange = signal<'1M' | '3M' | '6M' | '1Y'>('1Y');
  readonly chartRanges = ['1M', '3M', '6M', '1Y'] as const;
  readonly searchTerm = signal('');
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
  readonly usingPreviewData = computed(() => this.rankedAssets().length === 0);
  readonly portfolioAssets = computed(() => (this.rankedAssets().length ? this.leaders() : this.previewAssets()).slice(0, 10));
  readonly filteredAssets = computed(() => {
    const query = this.searchTerm().trim().toLowerCase();
    return query ? this.portfolioAssets().filter(asset => asset.stockName.toLowerCase().includes(query)) : this.portfolioAssets();
  });
  readonly periodReturn = computed(() => ({'1M': 3.2, '3M': 7.8, '6M': 11.6, '1Y': 14.7})[this.chartRange()]);
  readonly alpha = computed(() => Math.max(0, this.periodReturn() - 8.4).toFixed(1));
  readonly rangeStartLabel = computed(() => ({'1M': '26 Aug', '3M': '26 Jun', '6M': '26 Mar', '1Y': 'Sep 2025'})[this.chartRange()]);
  readonly trades = [
    {date: '26 Sep 2026', time: '14:32', symbol: 'ICICIBANK', type: 'BUY', quantity: 25, price: '1,282.40', gross: '32,060.00', charges: '80.35', net: '32,140.35', order: 'CNC', status: 'Pending T+1'},
    {date: '25 Sep 2026', time: '11:18', symbol: 'RELIANCE', type: 'SELL', quantity: 12, price: '2,991.15', gross: '35,893.80', charges: '69.55', net: '35,824.25', order: 'CNC', status: 'Pending T+1'},
    {date: '22 Sep 2026', time: '10:06', symbol: 'TRENT', type: 'BUY', quantity: 8, price: '6,842.50', gross: '54,740.00', charges: '103.20', net: '54,843.20', order: 'CNC', status: 'Settled'},
    {date: '18 Sep 2026', time: '15:04', symbol: 'INFY', type: 'SELL', quantity: 30, price: '1,604.20', gross: '48,126.00', charges: '92.10', net: '48,033.90', order: 'CNC', status: 'Settled'},
  ] as const;

  ngOnInit(): void { this.loadHistory(true); }

  assetTypeChanged(type: AssetDataType): void {
    this.assetType = type;
    this.result.set(null);
    this.error.set(null);
    this.loadLatestForType();
  }

  refresh(): void { this.loadHistory(true); }
  initials(name: string): string { return name.split(/\s+/).map(part => part[0]).join('').slice(0, 2).toUpperCase(); }
  assetColor(index: number): string { return ['#174d3e','#426f82','#b27b2f','#655c85','#36715f','#8d5a4a'][index % 6]; }
  weightFor(index: number): number { return [14, 13, 12, 11, 10, 10, 9, 8, 7, 6][index] ?? 5; }
  valueFor(index: number): string { return ['3,48,099','3,23,235','2,98,370','2,73,506','2,48,642','2,48,642','2,23,778','1,98,914','1,74,049','1,49,185'][index] ?? '1,24,321'; }
  quantityFor(index: number): number { return [52, 410, 180, 96, 42, 190, 38, 312, 54, 78][index] ?? 25; }
  averagePriceFor(index: number): string { return ['5,960.20','298.40','1,421.10','2,548.80','5,280.40','1,154.70','4,981.20','412.60','2,876.40','1,684.25'][index] ?? '1,250.00'; }
  ltpFor(index: number): string { return ['6,694.20','788.38','1,657.61','2,849.02','5,920.05','1,308.64','5,888.89','637.54','3,223.13','1,912.63'][index] ?? '1,480.00'; }
  dayPnlFor(index: number): string { return ['2,184','1,526','1,206','982','744','628','514','408','316','242'][index] ?? '180'; }
  overallPnlFor(index: number): string { return ['38,168','52,430','42,572','28,821','26,865','29,248','34,492','70,176','18,724','17,814'][index] ?? '8,240'; }
  momentumScore(asset: MomentumAsset): number { return Math.max(55, Math.min(98, Math.round(58 + asset.oneYearReturn * .72))); }

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
      error: () => this.executions.set([])
    });
  }

  private loadLatestForType(): void {
    const latest = this.executions().find(execution => execution.assetDataType === this.assetType);
    if (latest) this.loadExecution(latest);
  }

  private previewAssets(): MomentumAsset[] {
    const names = this.assetType === 'ETF'
      ? ['MOM100', 'NIFTYBEES', 'GOLDBEES', 'BANKBEES', 'ITBEES', 'JUNIORBEES', 'MON100', 'AUTOBEES', 'PHARMABEES', 'MID150BEES']
      : this.assetType === 'INDEX'
        ? ['Nifty Alpha 50', 'Nifty 200 Momentum 30', 'Nifty Midcap 150', 'Nifty IT', 'Nifty Auto', 'Nifty Bank', 'Nifty Pharma', 'Nifty Next 50', 'Nifty 500', 'Nifty 50']
        : ['TRENT', 'BEL', 'BHARTIARTL', 'M&M', 'BAJAJ-AUTO', 'ICICIBANK', 'HAL', 'COALINDIA', 'LT', 'SUNPHARMA'];
    const returns = [44.8, 39.2, 35.7, 32.1, 29.4, 27.8, 24.6, 22.3, 19.8, 17.5];
    return names.map((stockName, index) => ({stockName, oneYearReturn: returns[index], sixMonthReturn: returns[index] * .61, threeMonthReturn: returns[index] * .31, qualifiesForMomentum: true, strategyRunDate: this.today, totalRankScore: index + 1}));
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
