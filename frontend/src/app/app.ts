import { UserManagementComponent } from './user-management/user-management.component';
import { AuthService } from './auth/auth.service';
import { LoginComponent } from './auth/login.component';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
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
import { SystemMetricsComponent } from './system-metrics/system-metrics.component';
type Page = 'users' | 'system-metrics' | 'dashboard' | 'login' | 'price' | 'stocks' | 'etfs' | 'indexes' | 'nifty-index-stock' | 'momentum' | 'momentum-backtest' | 'momentum-risk-overlay' | 'risk-adjusted-momentum' | 'risk-adjusted-momentum-backtest' | 'market-breadth' | 'breadth-backtest' | 'technical-indicator';
type NavSectionKey = 'overview' | 'analyze' | 'backtest' | 'data' | 'administration';
type NavAccess = 'public' | 'authenticated' | 'admin' | 'superadmin';
interface NavItem { page: Page; label: string; description: string; icon: string; access: NavAccess; }
interface NavSection { key: NavSectionKey; label: string; items: readonly NavItem[]; }
const ADMIN_PAGES: readonly Page[] = ['price', 'stocks', 'etfs', 'indexes', 'nifty-index-stock'];
const SUPERADMIN_PAGES: readonly Page[] = ['users', 'system-metrics'];
const LOGGED_IN_HOME: Page = 'market-breadth';
const PAGE_PATHS: Record<Page, string> = {
  dashboard: '/overview/dashboard', login: '/login', 'market-breadth': '/overview/market-breadth',
  momentum: '/analyze/momentum', 'risk-adjusted-momentum': '/analyze/risk-adjusted-momentum',
  'technical-indicator': '/analyze/technical-indicators', 'momentum-backtest': '/backtest/momentum',
  'momentum-risk-overlay': '/backtest/risk-overlay', 'risk-adjusted-momentum-backtest': '/backtest/risk-adjusted',
  'breadth-backtest': '/backtest/breadth', price: '/data/price-updates', stocks: '/data/stocks',
  etfs: '/data/etfs', indexes: '/data/indices', 'nifty-index-stock': '/data/index-constituents',
  users: '/administration/users', 'system-metrics': '/administration/system-metrics'
};
const PATH_PAGES = new Map(Object.entries(PAGE_PATHS).map(([page, path]) => [path, page as Page]));
const NAV_SECTIONS: readonly NavSection[] = [
  { key: 'overview', label: 'Overview', items: [
    { page: 'dashboard', label: 'Strategy Dashboard', description: 'Strategy overview', icon: '◫', access: 'public' },
    { page: 'market-breadth', label: 'Market Breadth', description: 'Regime and breadth indicators', icon: '⌗', access: 'authenticated' },
  ]},
  { key: 'analyze', label: 'Analyze', items: [
    { page: 'momentum', label: 'Momentum', description: 'Calculate and rank assets', icon: '↗', access: 'authenticated' },
    { page: 'risk-adjusted-momentum', label: 'Risk-Adjusted Momentum', description: 'Momentum with volatility', icon: '◈', access: 'authenticated' },
    { page: 'technical-indicator', label: 'Technical Indicators', description: 'EMA and SuperTrend signals', icon: '⌁', access: 'authenticated' },
  ]},
  { key: 'backtest', label: 'Backtest', items: [
    { page: 'momentum-backtest', label: 'Momentum Backtest', description: 'Top-10 / top-20 strategy', icon: '◫', access: 'authenticated' },
    { page: 'momentum-risk-overlay', label: 'Risk Overlay', description: 'Stops, breadth and regime', icon: '⛨', access: 'authenticated' },
    { page: 'risk-adjusted-momentum-backtest', label: 'Risk-Adjusted Backtest', description: 'Inverse-vol sizing with stops', icon: '⚖', access: 'authenticated' },
    { page: 'breadth-backtest', label: 'Breadth Backtest', description: 'Breadth-filtered results', icon: '⧉', access: 'authenticated' },
  ]},
  { key: 'data', label: 'Data', items: [
    { page: 'price', label: 'Price Updates', description: 'Update market prices', icon: '⌁', access: 'admin' },
    { page: 'stocks', label: 'Stocks', description: 'Manage stock records', icon: '▥', access: 'admin' },
    { page: 'etfs', label: 'ETFs', description: 'Manage ETF records', icon: '◇', access: 'admin' },
    { page: 'indexes', label: 'Indices', description: 'Manage index records', icon: '◎', access: 'admin' },
    { page: 'nifty-index-stock', label: 'Index Constituents', description: 'Manage constituent lists', icon: '▤', access: 'admin' },
  ]},
  { key: 'administration', label: 'Administration', items: [
    { page: 'users', label: 'Users', description: 'Accounts and roles', icon: '♙', access: 'superadmin' },
    { page: 'system-metrics', label: 'System Metrics', description: 'JVM, CPU and health', icon: '▥', access: 'superadmin' },
  ]},
];
type TimeFrame = 'DAILY' | 'WEEKLY';
type SourceKey = 'stock' | 'etf' | 'index';
type AppTheme = 'forest' | 'ocean' | 'apple' | 'contrast';
interface PriceSource { key: SourceKey; title: string; shortTitle: string; description: string; path: string; icon: string; }
interface PriceUpdateJob { id: string; status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'; message: string; processed: number; total: number; saved: number; failedSymbols: string; }
interface HistoryItem { id: number; sourceKey: SourceKey; title: string; timeFrame: TimeFrame; success: boolean; message: string; completedAt: Date; }
@Component({ selector: 'app-root', imports: [RouterOutlet, UserManagementComponent, SystemMetricsComponent, LoginComponent, FormsModule, DatePipe, StockMasterComponent, ETFMasterComponent, IndexMasterComponent, NiftyIndexStockComponent, MomentumAnalysisComponent, MomentumDashboardComponent, MomentumBacktestComponent, MomentumRiskOverlayBacktestComponent, RiskAdjustedMomentumAnalysisComponent, RiskAdjustedMomentumBacktestComponent, TechnicalIndicatorComponent, MarketBreadthComponent, BreadthBacktestComponent], templateUrl: './app.html', styleUrl: './app.scss' })
export class App {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
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
  readonly userInitials = computed(() => this.auth.user()?.username.slice(0, 2).toUpperCase() ?? '');
  readonly visibleNavSections = computed(() => NAV_SECTIONS
    .map(section => ({ ...section, items: section.items.filter(item => this.canAccess(item.access)) }))
    .filter(section => section.items.length > 0));
  readonly expandedSections = signal<Record<NavSectionKey, boolean>>(this.savedSections());
  private readonly pendingPage = signal<Page | null>(null);

  constructor() {
    this.router.events.pipe(filter(event => event instanceof NavigationEnd)).subscribe(event => {
      const path = event.urlAfterRedirects.split(/[?#]/, 1)[0];
      const page = PATH_PAGES.get(path) ?? 'dashboard';
      if (page === 'login' && this.pendingPage() && !this.auth.isAuthenticated()) return;
      const accessiblePage = this.accessiblePage(page);
      if (page !== accessiblePage) {
        if (accessiblePage === 'login') this.pendingPage.set(page);
        this.selectedPage.set(page);
        void this.router.navigateByUrl(PAGE_PATHS[accessiblePage], { replaceUrl: true });
        return;
      }
      this.selectedPage.set(page);
      this.openSectionFor(page);
    });
    effect(() => {
      const page = this.selectedPage();
      const accessiblePage = this.accessiblePage(page);
      if (accessiblePage === 'login' && page !== 'login') this.pendingPage.set(page);
      if (page !== accessiblePage) {
        queueMicrotask(() => void this.router.navigateByUrl(PAGE_PATHS[accessiblePage], { replaceUrl: true }));
      } else if (this.pendingPage() === page && this.auth.isAuthenticated()) {
        queueMicrotask(() => {
          this.pendingPage.set(null);
          void this.router.navigateByUrl(PAGE_PATHS[page], { replaceUrl: true });
        });
      }
    });
  }

  toggleSection(key: NavSectionKey): void {
    this.expandedSections.update(value => {
      const updated = { ...value, [key]: !value[key] };
      localStorage.setItem('quant-nav-sections', JSON.stringify(updated));
      return updated;
    });
  }
  navigate(page: Page, replaceUrl = false): void {
    const accessiblePage = this.accessiblePage(page);
    if (page === 'login') this.pendingPage.set(null);
    if (accessiblePage === 'login' && page !== 'login') this.pendingPage.set(page);
    this.selectedPage.set(page);
    this.openSectionFor(page);
    this.mobileNavigationOpen.set(false);
    void this.router.navigateByUrl(PAGE_PATHS[accessiblePage], { replaceUrl });
  }
  signedIn(): void {
    const requestedPage = this.pendingPage();
    this.pendingPage.set(null);
    this.navigate(requestedPage && this.accessiblePage(requestedPage) === requestedPage ? requestedPage : LOGGED_IN_HOME, true);
  }
  logout(): void {
    this.pendingPage.set(null);
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
  private savedTheme(): AppTheme {
    const value = localStorage.getItem('quant-theme');
    if (value === 'slate') return 'apple';
    return value === 'ocean' || value === 'apple' || value === 'contrast' ? value : 'forest';
  }
  private accessiblePage(page: Page): Page {
    if (page === 'dashboard' || page === 'login') return page;
    if (!this.auth.isAuthenticated()) return 'login';
    if (SUPERADMIN_PAGES.includes(page) && !this.auth.isSuperadmin()) return LOGGED_IN_HOME;
    if (ADMIN_PAGES.includes(page) && !this.auth.isAdmin()) return LOGGED_IN_HOME;
    return page;
  }
  private canAccess(access: NavAccess): boolean {
    if (access === 'public') return true;
    if (access === 'authenticated') return this.auth.isAuthenticated();
    if (access === 'admin') return this.auth.isAdmin();
    return this.auth.isSuperadmin();
  }
  private savedSections(): Record<NavSectionKey, boolean> {
    const defaults = { overview: true, analyze: false, backtest: false, data: false, administration: false };
    try {
      return { ...defaults, ...JSON.parse(localStorage.getItem('quant-nav-sections') ?? '{}') };
    } catch {
      return defaults;
    }
  }
  private openSectionFor(page: Page): void {
    const sectionKey = NAV_SECTIONS.find(section => section.items.some(item => item.page === page))?.key;
    if (!sectionKey || this.expandedSections()[sectionKey]) return;
    this.expandedSections.update(value => {
      const updated = { ...value, [sectionKey]: true };
      localStorage.setItem('quant-nav-sections', JSON.stringify(updated));
      return updated;
    });
  }
}
