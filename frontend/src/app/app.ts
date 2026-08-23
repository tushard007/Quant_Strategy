import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { StockMasterComponent } from './stock-master/stock-master.component';
import { ETFMasterComponent } from './etf-master/etf-master.component';
import { IndexMasterComponent } from './index-master/index-master.component';
import { MomentumAnalysisComponent } from './momentum-analysis/momentum-analysis.component';
import { TechnicalIndicatorComponent } from './technical-indicator/technical-indicator.component';
type TimeFrame = 'DAILY' | 'WEEKLY';
type SourceKey = 'stock' | 'etf' | 'index';
interface PriceSource { key: SourceKey; title: string; shortTitle: string; description: string; path: string; icon: string; }
interface HistoryItem { id: number; sourceKey: SourceKey; title: string; timeFrame: TimeFrame; success: boolean; message: string; completedAt: Date; }
@Component({ selector: 'app-root', imports: [FormsModule, DatePipe, StockMasterComponent, ETFMasterComponent, IndexMasterComponent, MomentumAnalysisComponent, TechnicalIndicatorComponent], templateUrl: './app.html', styleUrl: './app.scss' })
export class App {
  private readonly http = inject(HttpClient);
  readonly timeFrame = signal<TimeFrame>('DAILY');
  readonly loading = signal<Record<SourceKey, boolean>>({ stock: false, etf: false, index: false });
  readonly history = signal<HistoryItem[]>([]);
  readonly notice = signal<{ type: 'success' | 'error'; message: string } | null>(null);
  readonly activePage = signal<'price' | 'stocks' | 'etfs' | 'indexes' | 'momentum' | 'technical-indicator'>('price');
  readonly isAnyLoading = computed(() => Object.values(this.loading()).some(Boolean));
  readonly sources: PriceSource[] = [
    { key: 'stock', title: 'Stock Prices', shortTitle: 'stocks', description: 'Refresh historical OHLCV data for all stocks in the master list.', path: 'stock-Price', icon: '▥' },
    { key: 'etf', title: 'ETF Prices', shortTitle: 'ETFs', description: 'Refresh historical OHLCV data for exchange-traded funds.', path: 'ETF-Price', icon: '◇' },
    { key: 'index', title: 'Index Prices', shortTitle: 'indices', description: 'Refresh historical price data for configured market indices.', path: 'index-Price', icon: '⌁' }
  ];
  sync(source: PriceSource, selectedTimeFrame = this.timeFrame()): void {
    this.loading.update(value => ({ ...value, [source.key]: true })); this.notice.set(null);
    this.http.post(`/api/price-data/${source.path}/${selectedTimeFrame}`, null, { responseType: 'text' }).pipe(finalize(() => this.loading.update(value => ({ ...value, [source.key]: false })))).subscribe({ next: () => this.record(source, selectedTimeFrame, true, `${source.title} are now up to date.`), error: error => this.record(source, selectedTimeFrame, false, error?.error?.message || error?.error || 'The server could not complete the update.') });
  }
  syncAll(): void { this.sources.forEach(source => this.sync(source)); }
  retry(item: HistoryItem): void { const source = this.sources.find(value => value.key === item.sourceKey); if (source) this.sync(source, item.timeFrame); }
  clearHistory(): void { this.history.set([]); }
  private record(source: PriceSource, timeFrame: TimeFrame, success: boolean, message: string): void {
    const cleanMessage = typeof message === 'string' ? message : JSON.stringify(message);
    this.history.update(items => [{ id: Date.now() + Math.random(), sourceKey: source.key, title: source.title, timeFrame, success, message: cleanMessage, completedAt: new Date() }, ...items]);
    const frequency = timeFrame === 'DAILY' ? 'daily' : 'weekly';
    this.notice.set({ type: success ? 'success' : 'error', message: success ? `Success! ${source.title} have been updated with the latest ${frequency} price data.` : `We couldn't update ${source.title}. ${cleanMessage}` });
  }
}
