import {HttpClient, HttpParams} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {AssetDataType} from '../momentum-analysis/momentum-analysis.service';

export interface ExperimentRow {ticker:string;theme:string;asOfDate:string;close:number;ret12:number;ret6:number;ret3:number;ret1d:number;sma20:number;sma50:number;sma200:number;sma50Prev:number;high52:number;pctFromHigh:number;atr14:number;extension:number;vol20:number;vol50:number;lastVolume:number;rank12:number;rank6:number;rank3:number;p1:number;p2:number;p3:number;p4:number;p5:number;score:number;bucket:'CORE'|'SATELLITE'|'WATCH'|'AVOID';action:string;}
export interface PortfolioPosition {ticker:string;theme:string;bucket:string;weightPercent:number;score:number;}
export interface ExperimentResult {assetDataType:AssetDataType;asOfDate:string|null;universeSize:number;scoredCount:number;skippedCount:number;results:ExperimentRow[];portfolio:PortfolioPosition[];}

@Injectable({providedIn:'root'})
export class OhlcvExperimentService {
  private readonly http=inject(HttpClient);
  run(type:AssetDataType,asOfDate:string){return this.http.post<ExperimentResult>(`/api/ohlcv-experiment/run/${type}`,null,{params:this.params(asOfDate)});}
  private params(asOfDate:string){return asOfDate?new HttpParams().set('asOfDate',asOfDate):new HttpParams();}
}
