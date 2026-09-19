import { Component } from '@angular/core';
import { Routes } from '@angular/router';

@Component({ standalone: true, template: '' })
export class PageRouteComponent {}

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'overview/dashboard' },
  { path: 'login', component: PageRouteComponent },
  { path: 'overview/dashboard', component: PageRouteComponent },
  { path: 'overview/market-breadth', component: PageRouteComponent },
  { path: 'analyze/momentum', component: PageRouteComponent },
  { path: 'analyze/risk-adjusted-momentum', component: PageRouteComponent },
  { path: 'analyze/technical-indicators', component: PageRouteComponent },
  { path: 'backtest/momentum', component: PageRouteComponent },
  { path: 'backtest/risk-overlay', component: PageRouteComponent },
  { path: 'backtest/risk-adjusted', component: PageRouteComponent },
  { path: 'backtest/breadth', component: PageRouteComponent },
  { path: 'data/price-updates', component: PageRouteComponent },
  { path: 'data/stocks', component: PageRouteComponent },
  { path: 'data/etfs', component: PageRouteComponent },
  { path: 'data/indices', component: PageRouteComponent },
  { path: 'data/index-constituents', component: PageRouteComponent },
  { path: 'administration/users', component: PageRouteComponent },
  { path: 'administration/system-metrics', component: PageRouteComponent },
  { path: '**', redirectTo: 'overview/dashboard' },
];
