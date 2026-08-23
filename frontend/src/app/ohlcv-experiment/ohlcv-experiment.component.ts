import {DecimalPipe} from '@angular/common';
import {Component, computed, inject, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {finalize} from 'rxjs';
import {AssetDataType} from '../momentum-analysis/momentum-analysis.service';
import {ExperimentResult, OhlcvExperimentService} from './ohlcv-experiment.service';

@Component({selector:'app-ohlcv-experiment',imports:[FormsModule,DecimalPipe],templateUrl:'./ohlcv-experiment.component.html',styleUrls:['./ohlcv-experiment.component.scss','./ohlcv-experiment-run-only.scss']})
export class OhlcvExperimentComponent {
  private readonly service=inject(OhlcvExperimentService);
  readonly today=this.localDate(new Date()); readonly loading=signal(false); readonly result=signal<ExperimentResult|null>(null); readonly error=signal<string|null>(null); readonly bucket=signal<'ALL'|'CORE'|'SATELLITE'|'WATCH'|'AVOID'>('ALL');
  assetType:AssetDataType='STOCK'; asOfDate=this.today;
  readonly visibleRows=computed(()=>this.bucket()==='ALL'?(this.result()?.results??[]):(this.result()?.results??[]).filter(row=>row.bucket===this.bucket()));
  readonly coreCount=computed(()=>this.result()?.results.filter(row=>row.bucket==='CORE').length??0); readonly satelliteCount=computed(()=>this.result()?.results.filter(row=>row.bucket==='SATELLITE').length??0);
  runCached(){this.execute(()=>this.service.run(this.assetType,this.asOfDate));}
  typeChanged(type:AssetDataType){this.assetType=type;this.result.set(null);this.error.set(null);}
  private execute(request:()=>ReturnType<OhlcvExperimentService['run']>){if(!this.asOfDate||this.asOfDate>this.today){this.error.set('Select today or an earlier date.');return;}this.loading.set(true);this.error.set(null);request().pipe(finalize(()=>this.loading.set(false))).subscribe({next:value=>{this.result.set(value);this.bucket.set('ALL');},error:error=>this.error.set(error?.error?.detail||error?.error?.message||'The OHLCV experiment could not be completed.')});}
  private localDate(date:Date){return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;}
}
