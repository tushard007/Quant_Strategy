import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {CurrencyPipe,DatePipe,DecimalPipe,PercentPipe} from '@angular/common';
import {finalize} from 'rxjs';
import {RiskAdjustedMomentumBacktestExecutionSummary,RiskAdjustedMomentumBacktestResult,RiskAdjustedMomentumBacktestService,Rebalance} from './risk-adjusted-momentum-backtest.service';

@Component({
  selector: 'app-risk-adjusted-momentum-backtest',
  imports: [FormsModule,CurrencyPipe,DatePipe,DecimalPipe,PercentPipe],
  templateUrl: './risk-adjusted-momentum-backtest.component.html',
  styleUrls: ['../momentum-backtest/momentum-backtest.component.scss','../momentum-backtest/momentum-backtest-results.scss','../momentum-backtest/momentum-backtest-toggle.scss']
})
export class RiskAdjustedMomentumBacktestComponent implements OnInit {
  private readonly service=inject(RiskAdjustedMomentumBacktestService);
  readonly tab = signal<'overview' | 'rebalances' | 'stops' | 'regime'>('overview');
  readonly loading=signal(false); readonly result=signal<RiskAdjustedMomentumBacktestResult|null>(null); readonly selected=signal<Rebalance|null>(null); readonly error=signal('');
  readonly executions=signal<RiskAdjustedMomentumBacktestExecutionSummary[]>([]); readonly currentRunId=signal<string|null>(null);
  readonly setup = {
    startDate: '2021-09-01', endDate: new Date().toISOString().slice(0, 10), initialCapital: 1000000,
    benchmark: 'NIFTY 500', entryRank: 20, retentionRank: 40, transactionCostPercent: .25, slippagePercent: .1, riskFreeRatePercent: 6,
    rebalanceMode: 'EQUAL_WEIGHT', bufferAmount: 200000, maximumLeverageAmount: 0,
    borrowingInterestRatePercent: 10, stopModel: 'ATR', trailingStopPercent: 0, atrPeriod: 14, atrMultiplier: 3,
    cooldownWeeks: 2, benchmarkSmaPeriod: 200, breadthThresholdPercent: 20, weakExposureCapPercent: 50
  };
  ngOnInit(){this.refreshExecutions();}
  run(){this.loading.set(true);this.error.set('');this.service.run(this.setup).pipe(finalize(()=>this.loading.set(false))).subscribe({next:value=>{this.result.set(value.result);this.currentRunId.set(value.runId);this.refreshExecutions();this.tab.set('overview');},error:error=>this.error.set(error?.error?.message||error?.error?.detail||'Risk-adjusted momentum backtest failed.')});}
  loadExecution(runId:string){if(!runId)return;this.loading.set(true);this.error.set('');this.service.execution(runId).pipe(finalize(()=>this.loading.set(false))).subscribe({next:value=>{this.result.set(value);this.currentRunId.set(runId);this.selected.set(null);},error:error=>this.error.set(error?.error?.message||error?.error?.detail||'Could not load the saved execution.')});}
  refreshExecutions(){this.service.history().subscribe({next:value=>this.executions.set(value),error:()=>this.executions.set([])});}
  export(){const request=this.currentRunId()?this.service.exportExecution(this.currentRunId()!):this.service.export(this.setup);request.subscribe(blob=>{const data=this.result();const url=URL.createObjectURL(blob);const anchor=document.createElement('a');anchor.href=url;anchor.download=`risk-adjusted-momentum-backtest-${data?.startDate||this.setup.startDate}-to-${data?.endDate||this.setup.endDate}.xlsx`;anchor.click();URL.revokeObjectURL(url);});}
  show(item:Rebalance){this.selected.update(current=>current?.signalDate===item.signalDate?null:item);this.tab.set('rebalances');}
  count(item:Rebalance,action:'KEEP'|'SELL'|'BUY'){return item.decisions.filter(value=>value.action===action).length;}
}
