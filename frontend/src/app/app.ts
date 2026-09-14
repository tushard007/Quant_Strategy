import { UserManagementComponent } from './user-management/user-management.component';
import { AuthService } from './auth/auth.service';
import { LoginComponent } from './auth/login.component';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { exhaustMap, filter, finalize, retry, switchMap, take, tap, timer } from 'rxjs';
import { StockMasterComponent } from './stock-master/stock-master.component';
import { ETFMasterComponent } from './etf-master/etf-master.component';
import { IndexMasterComponent } from './index-master/index-master.component';
import { NiftyIndexStockComponent } from './nifty-index-stock/nifty-index-stock.component';
import { MomentumAnalysisComponent } from './momentum-analysis/momentum-analysis.component';
import { MomentumDashboardComponent } from './momentum-dashboard/momentum-dashboard.component';
import { TechnicalIndicatorComponent } from './technical-indicator/technical-indicator.component';
import { MomentumBacktestComponent } from './momentum-backtest/momentum-backtest.component';
import { MomentumRiskOverlayBacktestComponent } from './momentum-risk-overlay-backtest/momentum-risk-overlay-backtest.component';
import { RiskAdjustedMomentumAnalysisComponent } from './risk-adjusted-momentum-analysis/risk-adjusted-momentum-analysis.component';
import { RiskAdjustedMomentumBacktestComponent } from './risk-adjusted-momentum-backtest/risk-adjusted-momentum-backtest.component';
import { MarketBreadthComponent } from './market-breadth/market-breadth.component';
import { BreadthBacktestComponent } from './breadth-backtest/breadth-backtest.component';
type Page = 'users' | 'dashboard' | 'login' | 'price' | 'stocks' | 'etfs' | 'indexes' | 'nifty-index-stock' | 'momentum' | 'momentum-backtest' | 'momentum-risk-overlay' | 'risk-adjusted-momentum' | 'risk-adjusted-momentum-backtest' | 'market-breadth' | 'breadth-backtest' | 'technical-indicator';
const ADMIN_PAGES: readonly Page[] = ['price', 'stocks', 'etfs', 'indexes', 'nifty-index-stock'];
const SUPERADMIN_PAGES: readonly Page[] = ['users'];
const LOGGED_IN_HOME: Page = 'market-breadth';
const PAGE_LABELS: Record<Page, string> = {
  dashboard: 'Momentum Dashboard', login: 'Sign in', price: 'Price Data Master', stocks: 'Stock Master',
  etfs: 'ETF Master', indexes: 'Index Master', 'nifty-index-stock': 'Nifty Index Stocks',
  momentum: 'Momentum Analysis', 'momentum-backtest': 'Momentum Backtest', 'momentum-risk-overlay': 'Risk Overlay Backtest',
  'risk-adjusted-momentum': 'Risk-Adjusted Momentum', 'risk-adjusted-momentum-backtest': 'Risk-Adjusted Momentum Backtest',
  'market-breadth': 'Market Breadth', 'breadth-backtest': 'Breadth Backtest', 'technical-indicator': 'Technical Indicator',
  users: 'Users'
};
type TimeFrame = 'DAILY' | 'WEEKLY';
type SourceKey = 'stock' | 'etf' | 'index';
type AppTheme = 'forest' | 'ocean' | 'slate' | 'contrast';
interface PriceSource { key: SourceKey; title: string; shortTitle: string; description: string; path: string; icon: string; }
interface PriceUpdateJob { id: string; status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'; message: string; processed: number; total: number; saved: number; failedSymbols: string; }
interface HistoryItem { id: number; sourceKey: SourceKey; title: string; timeFrame: TimeFrame; success: boolean; message: string; completedAt: Date; }
@Component({ selector: 'app-root', imports: [UserManagementComponent, LoginComponent, FormsModule, DatePipe, StockMasterComponent, ETFMasterComponent, IndexMasterComponent, NiftyIndexStockComponent, MomentumAnalysisComponent, MomentumDashboardComponent, MomentumBacktestComponent, MomentumRiskOverlayBacktestComponent, RiskAdjustedMomentumAnalysisComponent, RiskAdjustedMomentumBacktestComponent, TechnicalIndicatorComponent, MarketBreadthComponent, BreadthBacktestComponent], templateUrl: './app.html', styleUrl: './app.scss' })
export class App {
  private readonly http = inject(HttpClient);
  readonly auth = inject(AuthService);
  readonly mobileNavigationOpen = signal(false);
  readonly timeFrame = signal<TimeFrame>('DAILY');
  readonly loading = signal<Record<SourceKey, boolean>>({ stock: false, etf: false, index: false });
  readonly progress = signal<Partial<Record<SourceKey, PriceUpdateJob>>>({});
  readonly history = signal<HistoryItem[]>([]);
  readonly notice = signal<{ type: 'success' | 'error'; message: string } | null>(null);
  private readonly selectedPage = signal<Page>('dashboard');
  readonly activePage = computed<Page>(() => {
    const page = this.selectedPage();
    if (page === 'dashboard' || page === 'login') return page;
    if (!this.auth.isAuthenticated()) return 'login';
    if (SUPERADMIN_PAGES.includes(page) && !this.auth.isSuperadmin()) return LOGGED_IN_HOME;
    if (ADMIN_PAGES.includes(page) && !this.auth.isAdmin()) return LOGGED_IN_HOME;
    return page;
  });
  readonly pageLabel = computed(() => PAGE_LABELS[this.activePage()]);
  readonly userInitials = computed(() => this.auth.user()?.username.slice(0, 2).toUpperCase() ?? '');
  readonly expandedSections = signal<Record<'master' | 'analysis', boolean>>({ master: true, analysis: true });
  toggleSection(key: 'master' | 'analysis'): void {
    this.expandedSections.update(value => ({ ...value, [key]: !value[key] }));
  }
  navigate(page: Page): void {
    this.selectedPage.set(page);
    this.mobileNavigationOpen.set(false);
  }
  logout(): void {
    this.auth.logout();
    this.navigate('dashboard');
  }

  readonly theme = signal<AppTheme>(this.savedTheme());
  readonly isAnyLoading = computed(() => Object.values(this.loading()).some(Boolean));
  readonly sources: PriceSource[] = [
    { key: 'stock', title: 'Stock Prices', shortTitle: 'stocks', description: 'Refresh historical OHLCV data for all stocks in the master list.', path: 'stock-Price', icon: '▥' },
    { key: 'etf', title: 'ETF Prices', shortTitle: 'ETFs', description: 'Refresh historical OHLCV data for exchange-traded funds.', path: 'ETF-Price', icon: '◇' },
    { key: 'index', title: 'Index Prices', shortTitle: 'indices', description: 'Refresh historical price data for configured market indices.', path: 'index-Price', icon: '⌁' }
  ];
  sync(source: PriceSource, selectedTimeFrame = this.timeFrame()): void {
    if (!this.auth.isAdmin() || this.loading()[source.key]) return;
    this.loading.update(value => ({ ...value, [source.key]: true })); this.notice.set(null);
    this.progress.update(value => ({ ...value, [source.key]: undefined }));
    this.http.post<PriceUpdateJob>(`/api/price-data/jobs/${source.path}/${selectedTimeFrame}`, null).pipe(
      switchMap(job => timer(0, 2000).pipe(
        exhaustMap(() => this.http.get<PriceUpdateJob>(`/api/price-data/jobs/${job.id}`).pipe(retry({ count: 3, delay: 2000 }))),
        tap(status => this.progress.update(value => ({ ...value, [source.key]: status }))),
        filter(status => status.status === 'SUCCEEDED' || status.status === 'FAILED'),
        take(1)
      )),
      finalize(() => this.loading.update(value => ({ ...value, [source.key]: false })))
    ).subscribe({
      next: job => this.record(source, selectedTimeFrame, job.status === 'SUCCEEDED', job.message),
      error: () => this.record(source, selectedTimeFrame, false, 'Could not retrieve update status. The update may still be running; retry to reconnect.')
    });
  }
  syncAll(): void { this.sources.forEach(source => this.sync(source)); }
  retry(item: HistoryItem): void { const source = this.sources.find(value => value.key === item.sourceKey); if (source) this.sync(source, item.timeFrame); }
  clearHistory(): void { this.history.set([]); }
  setTheme(theme: AppTheme): void { this.theme.set(theme); localStorage.setItem('quant-theme', theme); }
  private record(source: PriceSource, timeFrame: TimeFrame, success: boolean, message: string): void {
    const cleanMessage = typeof message === 'string' ? message : JSON.stringify(message);
    this.history.update(items => [{ id: Date.now() + Math.random(), sourceKey: source.key, title: source.title, timeFrame, success, message: cleanMessage, completedAt: new Date() }, ...items]);
    const frequency = timeFrame === 'DAILY' ? 'daily' : 'weekly';
    this.notice.set({ type: success ? 'success' : 'error', message: success ? `Success! ${source.title} have been updated with the latest ${frequency} price data.` : `We couldn't update ${source.title}. ${cleanMessage}` });
  }
  private savedTheme(): AppTheme { const value = localStorage.getItem('quant-theme'); return value === 'ocean' || value === 'slate' || value === 'contrast' ? value : 'forest'; }
}
